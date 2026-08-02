package com.ntros.core.ecs.system;

import static com.ntros.AppConstants.MEADOW_SPAWN_CHANCE;
import static com.ntros.core.ecs.system.TickSystemHelper.cannotGrowHere;
import static com.ntros.core.ecs.system.TickSystemHelper.inBounds;

import com.ntros.core.world.World;
import com.ntros.core.world.terrain.TerrainCodec;
import com.ntros.core.world.terrain.Tile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BiomassGrowthSystem extends AbstractTickSystem {

  private static final Logger log = LoggerFactory.getLogger(BiomassGrowthSystem.class);

  private final TerrainCodec terrainCodec = new TerrainCodec();
  private static final float BASE_GROWTH = 5.50f;
  private static final int UPDATE_TOTAL = 50;
  private static final float MAX_GROWTH_THRESHOLD = 100.0f;
  private static final float NIL = 0.000000000000000f;

  /**
   * Seeded once here, never re-seeded: calling setSeed per tick resets the sequence and makes every
   * tick select the same tiles forever.
   */
  public BiomassGrowthSystem(long seed) {
    super(seed);
  }

  // select N random tiles to grow instead of a sweeping update
  // index selection is random, the same index can be selected more than once in a tick which
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
      growPlant(biomass, terrain, idx, width, height);
      // Once clusters are eaten, food is very hard to find
      // occasionally grow a whole cluster
//      if (MEADOW_SPAWN_CHANCE >= rng.nextFloat()) {
//        growMeadow(biomass, terrain, idx, width, height);
//      } else {
//        growPlant(biomass, terrain, idx, width, height);
//      }
    }
  }

  private void growMeadow(float[] biomass, byte[] terrain, int idx, int width, int height) {
    int meadowRadius = rng.nextInt(3, 7);
    for (int dx = -meadowRadius; dx <= meadowRadius; dx++) {
      for (int dy = -meadowRadius; dy <= meadowRadius; dy++) {
        if (dx * dx + dy * dy > meadowRadius * meadowRadius) {
          continue;
        }
        growPlant(biomass, terrain, idx, width, height);
      }
    }
  }

  // increase qty by BASE_GROWTH each tick. if a qty reaches max, populate a new tile next
  // to i in a random direction
  private void growPlant(float[] biomass, byte[] terrain, int idx, int width, int height) {
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
      // grow if empty, else increment if less than max
      if (biomass[neighIdx] == NIL) {
        biomass[neighIdx] = BASE_GROWTH;
      } else if (biomass[neighIdx] < MAX_GROWTH_THRESHOLD) {
        biomass[neighIdx] += BASE_GROWTH;
      }
    } else {
      biomass[idx] += BASE_GROWTH;
    }
  }
}
