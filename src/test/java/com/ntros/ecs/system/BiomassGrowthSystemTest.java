package com.ntros.ecs.system;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ntros.core.world.World;
import com.ntros.core.world.terrain.TerrainCodec;
import com.ntros.core.world.terrain.Tile;
import com.ntros.core.world.terrain.WorldTerrainSettings;
import com.ntros.generator.Terrain;
import com.ntros.graphics.rendering.data.Dimensions2d;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BiomassGrowthSystemTest {

  private static final int WIDTH = 32;
  private static final int HEIGHT = 32;
  private static final long SEED = 42L;

  @Test
  void growthSelectsDifferentTilesAcrossTicks() {
    World world = allGrassWorld();
    BiomassGrowthSystem system = new BiomassGrowthSystem(world.getSeed());

    for (long tick = 0; tick < 500; tick++) {
      system.update(world, tick);
    }

    int touchedTiles = 0;
    float total = 0;
    for (float quantity : world.getBiomass()) {
      if (quantity > 0) {
        touchedTiles++;
      }
      total += quantity;
    }

    assertTrue(total > 0, "growth must add biomass on a growable world");
    // Regression guard: re-seeding the RNG inside update() makes every tick pick the same
    // UPDATE_TOTAL indexes, so only that many tiles ever change.
    assertTrue(
        touchedTiles > 7,
        "growth must reach different tiles across ticks, got only " + touchedTiles);
  }

  private static World allGrassWorld() {
    int size = WIDTH * HEIGHT;
    byte[] tiles = new byte[size];
    Arrays.fill(tiles, new TerrainCodec().encodeTile(Tile.GRASS));

    Dimensions2d dimensions = new Dimensions2d(WIDTH, HEIGHT);
    Terrain terrain = new Terrain(tiles, new float[size], new float[size], dimensions);
    return World.of(new WorldTerrainSettings(dimensions, SEED), terrain, new float[size]);
  }
}
