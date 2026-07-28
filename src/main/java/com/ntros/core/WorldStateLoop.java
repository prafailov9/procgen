package com.ntros.core;

import com.ntros.core.channel.Channel;
import com.ntros.core.command.Command;
import com.ntros.core.clock.TickingClock;
import com.ntros.core.world.World;
import com.ntros.core.world.WorldSnapshot;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the world on its own thread: drains UI commands, advances the clock and world state, and
 * publishes immutable snapshots for the renderer. The world itself is never touched by any other
 * thread.
 */
public class WorldStateLoop implements Runnable {

  /// Sim time constants
  private static final float BASE_TICK_SECONDS = 1 / 60f; // game-time length of one tick at 1×
  private static final int MAX_TICKS_PER_FRAME = 8; // catch-up cap so a stall can't spiral
  private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L; // publish at most 10/sec
  private static final long FRAME_SLEEP_MS = 5;
  private static final int MAX_SPEED_BATCH = 1000; // ticks per cancel-check when unthrottled

  private final World world;
  private final TickingClock clock;
  private final Channel channel;
  private final AtomicReference<WorldSnapshot> latestSnapshot;
  private final CancellationToken token;

  ///  sim control + fast-forward mechanics
  private volatile double speed = 1.0; // 0 = paused
  private volatile boolean maxSpeed = false; // true = unthrottled

  public WorldStateLoop(
      World world,
      TickingClock clock,
      Channel channel,
      AtomicReference<WorldSnapshot> latestSnapshot,
      CancellationToken token) {
    this.world = world;
    this.clock = clock;
    this.channel = channel;
    this.latestSnapshot = latestSnapshot;
    this.token = token;
    // seed the renderer with the initial state so the panel has something to show immediately
    latestSnapshot.set(WorldSnapshot.of(world, clock.currentTime()));
  }

  public void run() {
    long last = System.nanoTime();
    long lastPublishNanos = 0;
    long publishedTick = -1;
    double accumulator = 0;

    System.out.println("Entered state loop...");
    while (!token.isCancelRequested()) {
      drainCommands();

      long now = System.nanoTime();
      double elapsedSeconds = (now - last) / 1_000_000_000.0;
      last = now;

      if (maxSpeed) {
        for (int i = 0; i < MAX_SPEED_BATCH && !token.isCancelRequested(); i++) {
          step();
        }
      } else {
        accumulator += speed * elapsedSeconds;
        int ticks = 0;
        while (accumulator >= BASE_TICK_SECONDS && ticks < MAX_TICKS_PER_FRAME) {
          step();
          accumulator -= BASE_TICK_SECONDS;
          ticks++;
        }
        if (ticks == MAX_TICKS_PER_FRAME) {
          accumulator = 0; // running behind; drop the debt instead of spiraling
        }
      }

      long currentTick = clock.currentTime();
      if (now - lastPublishNanos >= SNAPSHOT_INTERVAL_NANOS && currentTick != publishedTick) {
        latestSnapshot.set(WorldSnapshot.of(world, currentTick));
        lastPublishNanos = now;
        publishedTick = currentTick;
      }

      if (!maxSpeed) {
        try {
          Thread.sleep(FRAME_SLEEP_MS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt(); // stop() interrupts us; loop re-checks the token
        }
      }
    }
    System.out.println("state loop cancelled. Exiting...");
  }

  private void step() {
    // TODO: advance world state (world.step()) once entities/agents exist
    clock.tick();
  }

  private void drainCommands() {
    Command command;
    while ((command = channel.poll()) != null) {
      apply(command);
    }
  }

  private void apply(Command command) {
    // TODO: interpret payloads (pause, speed changes) once a command vocabulary exists
  }

  public void setSpeed(double speed) {
    this.speed = Math.max(0, speed);
  }

  public void setMaxSpeed(boolean maxSpeed) {
    this.maxSpeed = maxSpeed;
  }
}
