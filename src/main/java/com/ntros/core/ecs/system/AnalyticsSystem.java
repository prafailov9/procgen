package com.ntros.core.ecs.system;

import com.ntros.core.world.World;

/**
 * Samples the simulation on fixed SIM-TIME boundaries and publishes an immutable StatsSnapshot.
 *
 * <p>Runs last in the tick order so a sample sees the world exactly as this tick left it, including
 * the births and deaths LifecycleSystem just applied.
 *
 * <p>Sampling here rather than in the renderer is what makes the series analysable. Sampling on the
 * UI side means samples arrive on the snapshot publish cadence (10/sec of WALL time), so at X250
 * roughly 1,500 ticks pass between them - an "hourly" series would silently keep 1 hour in 25, with
 * gaps that vary by speed setting and machine load. Cross-correlating a non-uniformly sampled
 * series produces a meaningless phase lag, which is exactly the measurement this exists to support.
 * On tick boundaries the series is uniform in sim time, identical at every speed, and reproducible
 * from the seed.
 */
public class AnalyticsSystem implements TickSystem {

  /** One sim hour. The time base of the published series. */
  public static final int TICKS_PER_SAMPLE = 60;

  private static final int TICKS_PER_DAY = 1440;

  @Override
  public void update(World world, long tick) {
    var stats = world.getWorldStats();

    // Close the daily flow window first, so a sample taken on a day boundary publishes the day
    // that just ended rather than a day that is one tick old.
    if (tick % TICKS_PER_DAY == 0) {
      stats.rollDailyFlows();
    }

    if (tick % TICKS_PER_SAMPLE != 0) {
      return;
    }

    stats.recomputePopulations(world);
    stats.recordHistorySample();
    // publish to ui thread
    world.publishStats(stats.toSnapshot(tick, TICKS_PER_SAMPLE));
  }
}
