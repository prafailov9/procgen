package com.ntros.core.world;

import com.ntros.core.ecs.data.CreatureType;
import com.ntros.core.ecs.data.DeathCause;
import com.ntros.core.ecs.data.Motive;
import com.ntros.core.world.snapshot.StatsSnapshot;

import java.util.Arrays;

/**
 * Mutable analytics accumulator, owned by the World and therefore by the sim thread alone.
 * AnalyticsSystem drives it; nothing outside the sim thread may touch it. The UI reads the
 * immutable {@link StatsSnapshot} this produces instead.
 *
 * <p>Two kinds of numbers live here, and the distinction is the whole point:
 *
 * <ul>
 *   <li><b>Stocks</b> - populations, biomass, motive counts. Recomputed by scanning the world.
 *   <li><b>Flows</b> - births and deaths per day. Accumulated as they happen, because they cannot
 *       be recovered by looking at the world afterwards. A population sitting at 300 could be a
 *       dead-calm 0 births/0 deaths or a churning 90/90, and those need opposite fixes.
 * </ul>
 */
public class WorldStats {

  private static final int SPECIES_COUNT = CreatureType.values().length;
  private static final int CAUSE_COUNT = DeathCause.values().length;

  /// Population history: a ring buffer sampled on fixed sim-tick boundaries. Predator-prey
  /// dynamics are phase relationships (prey peak, predators peak later, prey crash) which a
  /// single-frame readout cannot show - you have to see the curves side by side over time.
  public static final int HISTORY_SAMPLES = 336; // 14 sim days at one sample per sim-hour

  // stocks
  private int statRabbits;
  private int statFoxes;
  private double statBiomassTotal;
  // one counter per Motive ordinal, across all species
  private final int[] statMotiveCounts = new int[Motive.values().length];

  // flows, accumulating over the day in progress
  private final int[] birthsToday = new int[SPECIES_COUNT];
  private final int[][] deathsToday = new int[SPECIES_COUNT][CAUSE_COUNT];
  // flows of the last COMPLETE day - what gets published, so the published rate always covers a
  // full day rather than however much of today happens to have elapsed
  private final int[] birthsLastDay = new int[SPECIES_COUNT];
  private final int[][] deathsLastDay = new int[SPECIES_COUNT][CAUSE_COUNT];

  private final int[] rabbitHistory = new int[HISTORY_SAMPLES];
  private final int[] foxHistory = new int[HISTORY_SAMPLES];
  private int historyCount;
  private int historyWriteIndex;

  /// Flow recording - called by LifecycleSystem as births and deaths are actually applied.

  public void recordBirth(byte species) {
    birthsToday[species]++;
  }

  public void recordDeath(byte species, DeathCause cause) {
    deathsToday[species][cause.ordinal()]++;
  }

  /** Closes the day: today's tallies become the published rates and a fresh day starts. */
  public void rollDailyFlows() {
    System.arraycopy(birthsToday, 0, birthsLastDay, 0, SPECIES_COUNT);
    Arrays.fill(birthsToday, 0);
    for (int species = 0; species < SPECIES_COUNT; species++) {
      System.arraycopy(deathsToday[species], 0, deathsLastDay[species], 0, CAUSE_COUNT);
      Arrays.fill(deathsToday[species], 0);
    }
  }

  /// Stock recording.

  /**
   * Rescans the world. O(alive creatures + tiles), so it runs on sample boundaries rather than
   * every tick - the biomass sum alone is 2M float adds on a big world.
   */
  public void recomputePopulations(World world) {
    int rabbits = 0;
    int foxes = 0;
    Arrays.fill(statMotiveCounts, 0);

    var store = world.getCreatureStore();
    var alive = store.getAlive();
    byte rabbitOrdinal = (byte) CreatureType.RABBIT.ordinal();
    for (int id = alive.nextSetBit(0); id >= 0; id = alive.nextSetBit(id + 1)) {
      if (store.species()[id] == rabbitOrdinal) {
        rabbits++;
      } else {
        foxes++;
      }
      statMotiveCounts[store.intentMotive()[id]]++;
    }

    double biomassTotal = 0;
    for (float quantity : world.getBiomass()) {
      biomassTotal += quantity;
    }

    statRabbits = rabbits;
    statFoxes = foxes;
    statBiomassTotal = biomassTotal;
  }

  /** Appends the current populations to the rolling history. */
  public void recordHistorySample() {
    rabbitHistory[historyWriteIndex] = statRabbits;
    foxHistory[historyWriteIndex] = statFoxes;
    historyWriteIndex = (historyWriteIndex + 1) % HISTORY_SAMPLES;
    if (historyCount < HISTORY_SAMPLES) {
      historyCount++;
    }
  }

  /** Builds the immutable value the UI and log writer consume. */
  public StatsSnapshot toSnapshot(long tick, int ticksPerSample) {
    int[][] deathsCopy = new int[SPECIES_COUNT][];
    for (int species = 0; species < SPECIES_COUNT; species++) {
      deathsCopy[species] = deathsLastDay[species].clone();
    }

    return new StatsSnapshot(
        tick,
        statRabbits,
        statFoxes,
        statBiomassTotal,
        statMotiveCounts.clone(),
        birthsLastDay.clone(),
        deathsCopy,
        linearizedHistory(rabbitHistory),
        linearizedHistory(foxHistory),
        ticksPerSample);
  }

  /**
   * Unrolls the ring into a plain oldest-first series. Once the buffer has wrapped, the oldest
   * entry sits at the write cursor. Doing this here means no consumer has to understand the ring.
   */
  private int[] linearizedHistory(int[] ring) {
    int[] series = new int[historyCount];
    int oldest = historyCount < HISTORY_SAMPLES ? 0 : historyWriteIndex;
    for (int i = 0; i < historyCount; i++) {
      series[i] = ring[(oldest + i) % HISTORY_SAMPLES];
    }
    return series;
  }
}
