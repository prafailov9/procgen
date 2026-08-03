package com.ntros.core.processor;

import com.ntros.core.CancellationToken;
import com.ntros.core.SimulationSpeed;
import com.ntros.core.channel.Channel;
import com.ntros.core.clock.TickingClock;
import com.ntros.core.command.ChangeSpeedCommand;
import com.ntros.core.command.Command;
import com.ntros.core.processor.timemanager.MutableTimeAccumulator;
import com.ntros.core.processor.timemanager.ProcessorTimeAccumulator;
import com.ntros.core.processor.timemanager.TimeAccumulator;
import com.ntros.core.updater.Actor;
import com.ntros.core.world.World;
import com.ntros.core.world.snapshot.WorldSnapshot;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the world on its own thread: drains UI commands, advances the clock and world state, and
 * publishes immutable snapshots for the renderer. The world itself is never touched by any other
 * thread.
 */
public class WorldStateProcessor implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(WorldStateProcessor.class);

  /// Sim time constants
  // game-time length of one tick at 1x speed
  private static final float BASE_TICK_SECONDS = 1 / 60f;
  // catch-up cap to prevent spiraling if the sim is stalling
  private static final int MAX_TICKS_PER_FRAME = 8;
  // publish at most 10/sec
  private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
  // min time of sleep between spins
  private static final long FRAME_SLEEP_MS = 5;

  // ticks per cancel-check at max speed
  private static final int MAX_UPDATES_COUNT = 18;

  private final World world;
  private final TickingClock clock;
  private final Actor actor;
  private final Channel<Command> channel;
  // lock-free, thread-safe cache for world updates
  private final AtomicReference<WorldSnapshot> latestSnapshot;
  private final CancellationToken token;
  private final MutableTimeAccumulator timeAccumulator = new ProcessorTimeAccumulator();

  /**
   * Sim speed multiplier. Buys simulation time. Elapsed time is multiplied by it to allow more
   * updates per frame. 1x speed buys 1 clock sim time, 5x buys 5, etc. 0 = paused
   */
  private volatile double speed = 1.0;

  /** Enables unthrottled state updates, capped by MAX_UPDATES_COUNT. true = unthrottled */
  private volatile boolean maxSpeed = false;

  public WorldStateProcessor(
      World world,
      TickingClock clock,
      Actor actor,
      Channel<Command> channel,
      AtomicReference<WorldSnapshot> latestSnapshot,
      CancellationToken token) {
    this.world = world;
    this.clock = clock;
    this.actor = actor;
    this.channel = channel;
    this.latestSnapshot = latestSnapshot;
    this.token = token;
    // seed the renderer with the initial state so the panel has something to show immediately
    latestSnapshot.set(WorldSnapshot.of(world, clock.currentTick()));
  }

  public void run() {
    long last = System.nanoTime();
    // prevents snapshots from being published too frequently
    timeAccumulator.setLastPublishTimeNanos(0);
    // clock ticks start at 0. -1 means no tick has been published yet
    // Tracks the last time the state was published. Prevents re-publishing the same snapshot
    timeAccumulator.setLastPublishedTick(-1);
    // Decides the exact number of updates each cycle
    timeAccumulator.setTimeBucket(0);
    log.info("Spinning World...");
    while (!token.isCancelRequested()) {
      drainCommands();

      // stamp the start time of this cycle
      long now = System.nanoTime();
      // measure the unprocessed time, between the end of last tick and now, for this cycle
      timeAccumulator.setElapsedRealTime((now - last) / 1_000_000_000.0);
      last = now;

      if (maxSpeed) {
        updateAtMax();
      } else {
        // update at current speed modifier. speed = 0 gets ignored naturally.
        updateAtRate();
      }

      tryPublish(now);

      if (!maxSpeed) {
        waitForNextFrame();
      }
    }
    log.info("Exiting StateLoop...");
  }

  public TimeAccumulator getSimStats() {
    return timeAccumulator;
  }

  /** try to publish once in a frame, at most 10 times a second */
  private void tryPublish(long now) {
    long currentTick = clock.currentTick();

    if (canPublish(now, currentTick)) {
      latestSnapshot.set(WorldSnapshot.of(world, currentTick));
      timeAccumulator.setLastPublishTimeNanos(now);
      timeAccumulator.setLastPublishedTick(currentTick);
    }
  }

  // only publish when the snapshot interval has elapsed and the state has been updated
  private boolean canPublish(long now, long tick) {
    return now - timeAccumulator.getLastPublishTimeNanos() >= SNAPSHOT_INTERVAL_NANOS
        && tick != timeAccumulator.getLastPublishedTick();
  }

  // when MaxSpeed is enabled, we bypass the accumulator
  // and spin as much as the CPU allows, updating the world and
  // clock MAX_UPDATES_COUNT times.
  private void updateAtMax() {
    for (int i = 0; i < MAX_UPDATES_COUNT && !token.isCancelRequested(); i++) {
      update();
    }
  }

  /**
   * The TimeBucket accumulates unprocessed simulation time. Each processor iteration, when speed >
   * 0, fills elapsed time from last tick till now, multiplied by the speed modifier. Processor
   * ticks only when the bucket has accumulated enough time: timeBucket >
   * BASE_TICK_SECONDS(16.66ms). Each update costs BASE_TICK_SECONDS, draining the bucket. Any
   * remainder stays in the bucket for the next loop so small time pieces(timeBucket <
   * BASE_TICK_SECONDS) aren't lost and are processed in the next iteration. If the timeBucket is
   * too large, produces MAX_TPS or more updates, reset it to prevent spiraling
   */
  private void updateAtRate() {
    // fill the bucket with the unprocessed time
    timeAccumulator.fillTimeBucket(speed * timeAccumulator.getElapsedRealTime());
    // track total updates
    int ticks = 0;
    while (canDrainBucket(ticks)) {
      update();
      timeAccumulator.drainTimeBucket(BASE_TICK_SECONDS);
      ticks++;
    }
    // reset the bucket if we hit the cap
    if (ticks == MAX_TICKS_PER_FRAME) {
      timeAccumulator.setTimeBucket(0);
    }
  }

  /**
   * Update only when the bucket has more than the min allowed time(BASE_TICK_SECONDS) and the ticks
   * guard cap. The cap is necessary because the bucket can become huge after a GC pause, debugging
   * or heavy updates, which will spiral into more and more accumulated time in subsequent frames.
   * The sim thread pins a core trying to repay an ever-growing debt, stops draining commands
   * promptly, and snapshots stall. The UI stays responsive but shows a stale world and ignores
   * commands
   */
  private boolean canDrainBucket(int currentTicks) {
    return timeAccumulator.getTimeBucket() >= BASE_TICK_SECONDS
        && currentTicks < MAX_TICKS_PER_FRAME;
  }

  /** sleeps the processor at minimum FRAME_SLEEP_MS */
  private void waitForNextFrame() {
    try {
      Thread.sleep(FRAME_SLEEP_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void update() {
    actor.act(world, clock.currentTick());
    clock.tick();
  }

  private void drainCommands() {
    Command command;
    while ((command = channel.poll()) != null) {
      apply(command);
    }
  }

  // TODO: route through a CommandExecutor
  private void apply(Command command) {
    if (command instanceof ChangeSpeedCommand changeSpeed) {
      SimulationSpeed target = changeSpeed.getSpeed();
      maxSpeed = target == SimulationSpeed.MAX;
      if (!maxSpeed) {
        speed = target.getSpeedValue();
      }
    }
  }
}
