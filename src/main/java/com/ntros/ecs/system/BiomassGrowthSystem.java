package com.ntros.ecs.system;

import static com.ntros.core.world.terrain.Tile.FOREST;
import static com.ntros.core.world.terrain.Tile.GRASS;

import com.ntros.core.world.World;
import com.ntros.core.world.terrain.TerrainCodec;
import com.ntros.core.world.terrain.Tile;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BiomassGrowthSystem implements TickSystem {

  private static final Logger log = LoggerFactory.getLogger(BiomassGrowthSystem.class);

  private final Random rng;
  private final TerrainCodec terrainCodec = new TerrainCodec();
  private static final float BASE_GROWTH = 0.05f;
  private static final int UPDATE_TOTAL = 7;
  private static final float MAX_GROWTH_THRESHOLD = 1000.0f;
  private static final float NIL = 0.000000000000000f;

  /**
   * Seeded once here, never re-seeded: calling setSeed per tick resets the sequence and makes
   * every tick select the same tiles forever.
   */
  public BiomassGrowthSystem(long seed) {
    rng = new Random(seed);
  }

  // select N random tiles to grow instead of a sweeping update
  // Don't know wtf to do with the tick. Maybe for observability
  // index selection is random, the same index can be selected more than once per tick which
  // produces naturally different growth rates for each biomass
  @Override
  public void update(World world, long tick) {
    var biomass = world.getBiomass();
    var terrain = world.getTerrain();
    int width = world.getWidth();
    int height = world.getHeight();
    int worldSize = width * height;

    // select N random indexes, if can grow, update quantity
    for (int i = 0; i < UPDATE_TOTAL; i++) {
      int idx = rng.nextInt(worldSize);
      Tile tile = terrainCodec.decode(terrain[idx]);
      if (cannotGrowHere(tile)) {
        continue;
      }
      applyGrowth(biomass, terrain, idx, width, height);
    }
  }

  // increase qty by 1 each tick. if a qty reaches max, populate a new tile next
  // to i in a random direction
  private void applyGrowth(float[] biomass, byte[] terrain, int idx, int width, int height) {
    int x = idx % width;
    int y = idx / width;
    if (biomass[idx] >= MAX_GROWTH_THRESHOLD) {
      // roll random direction
      int dir = rng.nextInt(2) == 0 ? -1 : 1;
      int nx = x + dir;
      int ny = y + dir;
      if (!inBounds(nx, ny, width, height)) {
        return;
      }
      int neighIdx = ny * width + nx;
      // check if selected neighbor is valid
      Tile neighTile = terrainCodec.decode(terrain[neighIdx]);
      if (cannotGrowHere(neighTile)) {
        return;
      }
      // set if empty, else increment if less than max
      if (biomass[neighIdx] == NIL) {
        biomass[neighIdx] = BASE_GROWTH;
      } else if (biomass[neighIdx] < MAX_GROWTH_THRESHOLD) {
        biomass[neighIdx] += BASE_GROWTH;
      }
    } else {
      biomass[idx] += BASE_GROWTH;
    }
  }

  private boolean inBounds(int x, int y, int w, int h) {
    return x >= 0 && x < w && y >= 0 && y < h;
  }

  private boolean cannotGrowHere(Tile tile) {
    return (tile != GRASS && tile != FOREST);
  }
}
