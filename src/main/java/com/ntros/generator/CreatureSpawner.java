package com.ntros.generator;

import static com.ntros.AppConstants.*;
import static com.ntros.ecs.store.CreatureType.FOX;
import static com.ntros.ecs.store.CreatureType.RABBIT;

import com.ntros.core.world.terrain.TerrainClassifier;
import com.ntros.ecs.store.CreatureStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public final class CreatureSpawner {

  private static final Logger log = LoggerFactory.getLogger(CreatureSpawner.class);
  private final Terrain terrain;
  private final Random rng;
  private final TerrainClassifier classifier = new TerrainClassifier();

  private int spawnedCreaturesCount = 0;

  public CreatureSpawner(Terrain terrain, long seed) {
    if (terrain == null) {
      throw new IllegalArgumentException("Empty terrain, nothing to spawn");
    }
    rng = new Random(seed + 1);
    this.terrain = terrain;
  }

  public CreatureStore spawnEntities() {
    int width = terrain.dimensions2d().width();
    int height = terrain.dimensions2d().height();
    CreatureStore creatureStore = new CreatureStore();
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        if (spawnedCreaturesCount >= CREATURES_CAPACITY) {
          return creatureStore;
        }
        // roll dice to spawn on a valid tile
        if (classifier.spawnChance(x, y, terrain) < rng.nextFloat()) {
          continue;
        }
        // spawn
        spawnedCreaturesCount++;
        int creatureId = creatureStore.spawn();
        creatureStore.age()[creatureId] = CREATURE_START_AGE;
        creatureStore.x()[creatureId] = x;
        creatureStore.y()[creatureId] = y;
        creatureStore.energy()[creatureId] = CREATURE_MAX_ENERGY;
        byte s;
        if (PREDATOR_SPAWN_CHANCE >= rng.nextFloat()) {
          s = (byte) FOX.ordinal();
        } else {
          s = (byte) RABBIT.ordinal();
        }
        creatureStore.species()[creatureId] = s;
      }
    }
    log.info("Spawned {} creatures", spawnedCreaturesCount);
    return creatureStore;
  }
}
