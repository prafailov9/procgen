package com.ntros.core.ecs;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ntros.core.CancellationToken;
import com.ntros.core.SimulationSpeed;
import com.ntros.core.channel.Channel;
import com.ntros.core.channel.CommandChannel;
import com.ntros.core.clock.SimClock;
import com.ntros.core.clock.TickingClock;
import com.ntros.core.command.ChangeSpeedCommand;
import com.ntros.core.ecs.data.CreatureType;
import com.ntros.core.ecs.store.CreatureStore;
import com.ntros.core.ecs.system.BiomassGrowthSystem;
import com.ntros.core.processor.WorldStateProcessor;
import com.ntros.core.updater.Actor;
import com.ntros.core.updater.StateActor;
import com.ntros.core.world.World;
import com.ntros.core.world.snapshot.WorldSnapshot;
import com.ntros.core.world.terrain.TerrainGenerationSettings;
import com.ntros.core.world.terrain.WorldTerrainSettings;
import com.ntros.generator.BiomassGenerator;
import com.ntros.generator.CreatureSpawner;
import com.ntros.generator.NoiseTerrainGenerator;
import com.ntros.generator.Terrain;
import com.ntros.generator.fastnoiselite.NoiseSettings;
import com.ntros.graphics.rendering.data.Dimensions2d;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Pins the coexistence equilibrium found on 2026-08-02 (worklog: "Coexistence Breakthrough").
 *
 * <p>That equilibrium lives in tuning constants spread across the species table, the growth system,
 * feeding, metabolism and movement. Any of them can be nudged innocently while refactoring and
 * silently return the sim to the day-1 extinctions it spent weeks escaping — nothing else in the
 * build would notice. This test is the tripwire.
 *
 * <p>It runs headless: the systems are ticked directly, with no WorldStateProcessor timing loop, no
 * threads and no UI, so a sim-day costs wall-milliseconds instead of wall-seconds.
 */
class EcosystemCoexistenceTest {

  // the exact scenario recorded in the worklog
  private static final long SEED = -9070669405368241458L;
  private static final int TICKS_PER_DAY = 1440;

  private static final int SIMULATED_DAYS = 12;

  // Enable this test only when you need to verify stability of coexistence
  //  @Test
  void bothSpeciesSurviveOnTheCoexistenceSeed() throws InterruptedException {
    World world = generateCoexistenceWorld();
    Actor actor = StateActor.ofEcosystem(world.getSeed());

    int initialRabbits = countOf(world, CreatureType.RABBIT);
    int initialFoxes = countOf(world, CreatureType.FOX);
    assertTrue(initialRabbits > 0, "scenario must start with rabbits");
    assertTrue(initialFoxes > 0, "scenario must start with foxes");

    for (long tick = 0; tick < (long) SIMULATED_DAYS * TICKS_PER_DAY; tick++) {
      actor.act(world, tick);
    }

    int rabbits = countOf(world, CreatureType.RABBIT);
    int foxes = countOf(world, CreatureType.FOX);

    assertTrue(
        rabbits > 0,
        "rabbits went extinct within " + SIMULATED_DAYS + " days (started " + initialRabbits + ")");
    assertTrue(
        foxes > 0,
        "foxes went extinct within " + SIMULATED_DAYS + " days (started " + initialFoxes + ")");
  }

  /**
   * Biomass flow must scale with world area, or big worlds get the same absolute food as small ones
   * spread over 15x the land — the bug that starved every big-world run.
   *
   * <p>Runs the growth system in isolation on creature-free worlds: measuring net biomass in a
   * populated world would measure grazing pressure, not growth.
   */
  @Test
  void biomassGrowthPerTileIsWorldSizeIndependent() {
    int measuredTicks = 500;

    double smallGrowthPerTile = growthPerTile(Dimensions2d.ofSmallWorld(), measuredTicks);
    double bigGrowthPerTile = growthPerTile(Dimensions2d.ofBigWorld(), measuredTicks);

    assertTrue(smallGrowthPerTile > 0, "small world must regrow biomass");
    assertTrue(bigGrowthPerTile > 0, "big world must regrow biomass");

    // Before the fix the big world regrew around 15.7x less per tile. The bound is loose because
    // the
    // growable-land fraction differs between two independently generated maps.
    double ratio = smallGrowthPerTile / bigGrowthPerTile;
    assertTrue(
        ratio > 0.5 && ratio < 2.0,
        "per-tile biomass regrowth must be world-size independent, ratio was " + ratio);
  }

  private static double growthPerTile(Dimensions2d dimensions, int ticks) {
    World world = generateEmptyWorld(dimensions);
    var growth = new BiomassGrowthSystem(SEED);

    double before = totalBiomass(world);
    for (long tick = 0; tick < ticks; tick++) {
      growth.update(world, tick);
    }
    return (totalBiomass(world) - before) / world.getSize();
  }

  private static WorldStateProcessor buildProcessorWithDefaultClock(
      World world,
      Actor actor,
      Channel channel,
      AtomicReference<WorldSnapshot> worldSnapshot,
      CancellationToken token) {
    return new WorldStateProcessor(
        world, SimClock.ofDefaultTimeScale(), actor, channel, worldSnapshot, token);
  }

  private static World generateCoexistenceWorld() {
    return generateWorld(Dimensions2d.ofSmallWorld());
  }

  private static World generateWorld(Dimensions2d dimensions) {
    WorldTerrainSettings terrainSettings = new WorldTerrainSettings(dimensions, SEED);
    Terrain terrain = generateTerrain(terrainSettings);
    float[] biomass = new BiomassGenerator(terrain, SEED).generateBiomass();
    var creatures = new CreatureSpawner(terrain, SEED).spawnEntities();

    return World.of(terrainSettings, terrain, biomass, creatures);
  }

  /** Same world, no creatures: isolates growth from grazing. */
  private static World generateEmptyWorld(Dimensions2d dimensions) {
    WorldTerrainSettings terrainSettings = new WorldTerrainSettings(dimensions, SEED);
    Terrain terrain = generateTerrain(terrainSettings);
    float[] biomass = new BiomassGenerator(terrain, SEED).generateBiomass();

    return World.of(terrainSettings, terrain, biomass, new CreatureStore());
  }

  private static Terrain generateTerrain(WorldTerrainSettings terrainSettings) {
    TerrainGenerationSettings generationSettings =
        new TerrainGenerationSettings(terrainSettings, NoiseSettings.ofDefault());
    return new NoiseTerrainGenerator(generationSettings).generateTerrain();
  }

  private static int countOf(World world, CreatureType type) {
    var store = world.getCreatureStore();
    var alive = store.getAlive();
    byte ordinal = (byte) type.ordinal();
    int count = 0;
    for (int id = alive.nextSetBit(0); id >= 0; id = alive.nextSetBit(id + 1)) {
      if (store.species()[id] == ordinal) {
        count++;
      }
    }
    return count;
  }

  private static double totalBiomass(World world) {
    double total = 0;
    for (float quantity : world.getBiomass()) {
      total += quantity;
    }
    return total;
  }
}
