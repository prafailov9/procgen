package com.ntros.core.world.snapshot;

import com.ntros.core.ecs.data.CreatureType;
import com.ntros.core.ecs.data.DeathCause;
import com.ntros.core.ecs.data.Motive;

/**
 * Immutable analytics value, produced on the sim thread by AnalyticsSystem and consumed by the
 * HUD (and, later, the log writer). Same contract as WorldSnapshot: built from copies, never
 * mutated after publish, so any number of readers on any thread are safe without locking.
 *
 * <p>History arrays arrive already linearized oldest-first, so consumers never need to know the
 * producer keeps a ring buffer.
 *
 * @param tick sim tick this sample was taken at
 * @param motiveCounts indexed by Motive ordinal
 * @param birthsPerDay indexed by CreatureType ordinal, over the last COMPLETE sim day
 * @param deathsPerDay [CreatureType ordinal][DeathCause ordinal], over the last COMPLETE sim day
 * @param ticksPerSample sim ticks between history samples — the series' time base
 */
public record StatsSnapshot(
    long tick,
    int rabbits,
    int foxes,
    double biomassTotal,
    int[] motiveCounts,
    int[] birthsPerDay,
    int[][] deathsPerDay,
    int[] rabbitHistory,
    int[] foxHistory,
    int ticksPerSample) {

  /** Placeholder so a freshly generated world can publish before AnalyticsSystem's first sample. */
  public static StatsSnapshot empty() {
    return new StatsSnapshot(
        0,
        0,
        0,
        0,
        new int[Motive.values().length],
        new int[CreatureType.values().length],
        new int[CreatureType.values().length][DeathCause.values().length],
        new int[0],
        new int[0],
        1);
  }

  public int births(CreatureType type) {
    return birthsPerDay[type.ordinal()];
  }

  public int deaths(CreatureType type, DeathCause cause) {
    return deathsPerDay[type.ordinal()][cause.ordinal()];
  }

  /** Total deaths of a species over the last complete day, all causes. */
  public int deaths(CreatureType type) {
    int total = 0;
    for (int count : deathsPerDay[type.ordinal()]) {
      total += count;
    }
    return total;
  }
}
