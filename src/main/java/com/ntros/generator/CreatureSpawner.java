package com.ntros.generator;

import static com.ntros.AppConstants.CREATURES_CAPACITY;
import static com.ntros.Main.SYSTEM_SEED;

import com.ntros.core.world.terrain.TerrainClassifier;
import com.ntros.ecs.store.CreatureStore;
import com.ntros.ecs.store.CreatureType;

import java.util.Random;

public final class CreatureSpawner {

  private final Terrain terrain;
  private final Random rng = new Random(SYSTEM_SEED);
  private final TerrainClassifier classifier = new TerrainClassifier();

  private int spawnedEntitiesCount = 0;

  public CreatureSpawner(Terrain terrain) {
    if (terrain == null) {
      throw new IllegalArgumentException("Empty terrain, nothing to spawn");
    }
    this.terrain = terrain;
  }

  private boolean inBounds(int x, int y) {
    return x >= 0
        && x < terrain.dimensions2d().width()
        && y >= 0
        && y < terrain.dimensions2d().height();
  }

  public CreatureStore spawnEntities() {
    int width = terrain.dimensions2d().width();
    int height = terrain.dimensions2d().height();
    CreatureStore creatureStore = new CreatureStore();
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        if (spawnedEntitiesCount >= CREATURES_CAPACITY) {
          return creatureStore;
        }
        if (!inBounds(x, y)) {
          continue;
        }
        // roll dice to spawn on a valid tile
        if (classifier.spawnChance(x, y, terrain) < rng.nextFloat()) {
          continue;
        }
        // spawn
        spawnedEntitiesCount++;
        int idx = y * width + x;
        if (idx >= CREATURES_CAPACITY) {
          continue;
        }

        creatureStore.age()[idx] = 1;
        creatureStore.x()[idx] = x;
        creatureStore.y()[idx] = y;
        creatureStore.alive().set(idx, true);
        creatureStore.energy()[idx] = 100.0f;
        byte s;
        if (rng.nextBoolean()) {
          s = (byte) CreatureType.FOX.ordinal();
        } else {
          s = (byte) CreatureType.RABBIT.ordinal();
        }
        creatureStore.species()[idx] = s;
        creatureStore.freeList()[idx] = 1;
      }
    }
    return creatureStore;
  }
}
