package com.ntros.generator;

import static com.ntros.AppConstants.CLUSTER_BIOMASS_CHANCE;
import static com.ntros.AppConstants.BIOMASS_SPAWN_CHANCE;
import static com.ntros.Main.SYSTEM_SEED;

import com.ntros.core.world.terrain.TerrainCodec;
import com.ntros.core.world.terrain.Tile;
import java.util.Random;

/**
 *
 *
 * <pre>
 * Right now, biomass means just food for herbivores. Later, can add new classification.
 * TODO: add different biomass types and rules
 * </pre>
 */
public class BiomassGenerator {
  private final Terrain terrain;
  private final Random rng = new Random(SYSTEM_SEED);
  private final TerrainCodec terrainCodec = new TerrainCodec();

  public BiomassGenerator(Terrain terrain) {
    if (terrain == null) {
      throw new IllegalArgumentException("Empty terrain");
    }
    if (terrain.tiles().length == 0
        || terrain.elevation().length == 0
        || terrain.moisture().length == 0) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid terrain data lengths. tilesLen=%s; elevationLen=%s; moistureLen=%s",
              terrain.tiles().length, terrain.elevation().length, terrain.moisture().length));
    }
    this.terrain = terrain;
  }

  // generates food for herbivores
  public byte[] generateBiomass() {
    var dimensions = terrain.dimensions2d();
    int width = dimensions.width();
    int height = dimensions.height();
    byte[] biomass = new byte[width * height];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int idx = y * width + x;

        // should food spawn at all this cycle
        if (BIOMASS_SPAWN_CHANCE <= rng.nextFloat()) {
          continue;
        }
        // roll dice to decide if cluster or single tile
        // small chance for clusters
        if (CLUSTER_BIOMASS_CHANCE >= rng.nextFloat()) {
          generateFoodCluster(x, y, biomass);
        } else {
          tryGrowFood(idx, biomass);
        }
      }
    }
    return biomass;
  }

  private void generateFoodCluster(int x, int y, byte[] biomass) {
    // select random cluster length
    int width = terrain.dimensions2d().width();
    int radius = rng.nextInt(2, 7);
    for (int dy = -radius; dy <= radius; dy++) {
      for (int dx = -radius; dx <= radius; dx++) {

        // exclude corners of the square bounding box
        if (dx * dx + dy * dy > radius * radius) {
          continue;
        }

        int nx = x + dx;
        int ny = y + dy;
        int idx = ny * width + nx;
        if (!inBounds(nx, ny)) {
          continue;
        }

        tryGrowFood(idx, biomass);
      }
    }
  }

  private void tryGrowFood(int idx, byte[] biomass) {
    if (canGrowFood(idx, biomass)) {
      int qty = rng.nextInt(1, 10);
      biomass[idx] = (byte) qty;
    }
  }

  private boolean inBounds(int x, int y) {
    return x >= 0
        && x < terrain.dimensions2d().width()
        && y >= 0
        && y < terrain.dimensions2d().height();
  }

  /** // skip if current already has a value // skip non-forest/grass */
  private boolean canGrowFood(int idx, byte[] biomass) {
    Tile tile = terrainCodec.decode(terrain.tiles()[idx]);
    return (tile == Tile.GRASS || tile == Tile.FOREST) && (int) biomass[idx] == 0;
  }
}
