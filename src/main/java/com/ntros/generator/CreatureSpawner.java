package com.ntros.generator;

import static com.ntros.AppConstants.*;
import static com.ntros.core.ecs.data.CreatureType.FOX;
import static com.ntros.core.ecs.data.CreatureType.RABBIT;

import com.ntros.core.world.terrain.TerrainCodec;
import com.ntros.core.world.terrain.Tile;
import com.ntros.core.ecs.store.CreatureStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public final class CreatureSpawner {

  private static final Logger log = LoggerFactory.getLogger(CreatureSpawner.class);
  private final Terrain terrain;
  private final Random rng;
  //  private final TerrainClassifier classifier = new TerrainClassifier();
  private final TerrainCodec terrainCodec = new TerrainCodec();

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
    int worldSize = width * height;

    Set<Integer> takenPositions = new HashSet<>();

    for (int i = 0; i < CREATURES_INITIAL_CAPACITY; i++) {
      int randIdx = rng.nextInt(worldSize);
      Tile tile = terrainCodec.decode(terrain.tiles()[randIdx]);
      // roll dice to spawn on a valid tile
      // ensure you spawn something. Generation needs to guarantee exactly CREATURES_CAPACITY
      // creatures are occupying the world
      while (takenPositions.contains(randIdx) || !canSpawnHere(tile)) {
        randIdx = rng.nextInt(worldSize);
        tile = terrainCodec.decode(terrain.tiles()[randIdx]);
      }

      spawn(randIdx % width, randIdx / width, creatureStore);

      takenPositions.add(randIdx);
    }

    return creatureStore;
  }

  private void spawn(int x, int y, CreatureStore creatureStore) {
    // spawn
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

  private boolean canSpawnHere(Tile tile) {
    return tile != Tile.SHALLOW_WATER && tile != Tile.DEEP_WATER;
  }
}
