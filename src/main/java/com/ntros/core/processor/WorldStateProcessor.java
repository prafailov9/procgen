package com.ntros.core.processor;

import com.ntros.core.CancellationToken;
import com.ntros.core.SimulationSpeed;
import com.ntros.core.channel.Channel;
import com.ntros.core.clock.TickingClock;
import com.ntros.core.command.ChangeSpeedCommand;
import com.ntros.core.command.Command;
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
  private final MutableSimStats stats = new ProcessorSimStats();

  /**
   * Sim speed multiplier. Buys simulation time. Elapsed time is multiplied by it to allow more
   * updates per frame. 1x speed buys 1 clock sim time, 5x buys 5, etc.
   */
  private volatile double speed = 1.0; // 0 = paused

  /** Enables unthrottled state updates, capped by MAX_UPDATES_COUNT */
  private volatile boolean maxSpeed = false; // true = unthrottled

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
    latestSnapshot.set(WorldSnapshot.of(world, clock.currentTime()));
  }

  public void run() {
    long last = System.nanoTime();
    // prevents snapshots from being published too frequently
    stats.setLastPublishTimeNanos(0);
    // clock ticks start at 0. -1 means no tick has been published yet
    // Tracks the last time the state was published. Prevents re-publishing the same snapshot
    stats.setLastPublishedTick(-1);
    // time bucket, deciding the exact number of updates for each loop
    stats.setTimeBucket(0);
    log.info("Spinning World...");
    while (!token.isCancelRequested()) {
      drainCommands();

      // stamp the start time of this spin
      long now = System.nanoTime();
      // measure real time in seconds that has elapsed between end of last tick and now
      stats.setElapsedRealTime((now - last) / 1_000_000_000.0);
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

  public SimStats getSimStats() {
    return stats;
  }

  /** try to publish once in a frame, at most 10 times a second */
  private void tryPublish(long now) {
    long currentTick = clock.currentTime();

    // only publish when the snapshot interval has elapsed and the state has been updated
    if (now - stats.getLastPublishTimeNanos() >= SNAPSHOT_INTERVAL_NANOS
        && currentTick != stats.getLastPublishedTick()) {
      latestSnapshot.set(WorldSnapshot.of(world, currentTick));
      stats.setLastPublishTimeNanos(now);
      stats.setLastPublishedTick(currentTick);
    }
  }

  // when MaxSpeed is enabled, we bypass the accumulator
  // and spin as much as the CPU allows, updating the world and
  // clock MAX_UPDATES_COUNT times.
  // TODO: this shi is fucking crazy, it just paints the whole
  //  map yellow instantly no matter the cap. Should probably default maxSpeed to x250
  private void updateAtMax() {
    for (int i = 0; i < MAX_UPDATES_COUNT && !token.isCancelRequested(); i++) {
      update();
    }
  }

  /**
   * The TimeBucket accumulates unprocessed simulation time. Each processor iteration, when speed >
   * 0, fills at minimum FRAME_SLEEP_MS, multiplied by the speed modifier. Processor ticks only when
   * the bucket has accumulated enough time: timeBucket > BASE_TICK_SECONDS(16.66ms). Each update
   * costs BASE_TICK_SECONDS, draining the bucket. Any remainder stays in the bucket for the next
   * loop so small time pieces aren't lost and are processed in the next iteration.
   */
  private void updateAtRate() {
    // calculate the total amount of updates in time for this spin
    // speed multiplier allocates more updates
    stats.fillTimeBucket(speed * stats.getElapsedRealTime());
    int ticks = 0;
    while (canDrainBucket(ticks)) {
      update();
      stats.drainTimeBucket(BASE_TICK_SECONDS);
      ticks++;
    }
    // reset the bucket if we hit the cap
    if (ticks == MAX_TICKS_PER_FRAME) {
      stats.setTimeBucket(0);
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
    return stats.getTimeBucket() >= BASE_TICK_SECONDS && currentTicks < MAX_TICKS_PER_FRAME;
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
    actor.act(world, clock.currentTime());
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
