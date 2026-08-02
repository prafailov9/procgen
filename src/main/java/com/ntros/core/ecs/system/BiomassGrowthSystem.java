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
  private static final float MAX_GROWTH_THRESHOLD = 100.0f;
  private static final float NIL = 0.000000000000000f;

  /**
   * Growth events per tile per tick, calibrated from the coexistence run: 50 events/tick on the
   * 490x270 small world.
   *
   * <p>This used to be a flat 50 events/tick for ANY world size, which made biomass flow an
   * absolute rate instead of a per-area one. A big world (1920x1080) has 15.7x more tiles but got
   * the same 275 biomass/tick, so per-tile regrowth was 15.7x slower there. Initial biomass IS
   * per-tile, so big worlds started proportionally stocked and then starved: rabbits exploded on
   * the standing crop, crashed to the (identical, absolute) flow-limited carrying capacity, and
   * foxes went extinct because the same rabbit count spread over 15.7x the area drops prey DENSITY
   * below what a fox's vision can find. Scaling by area makes the equilibrium
   * world-size-independent.
   */
  private static final float GROWTH_EVENTS_PER_TILE = 50.0f / (490.0f * 270.0f);

  /**
   * Fractional carry for the per-tick event budget, same idea as the processor's time bucket:
   * worlds too small to earn a whole event per tick accumulate the remainder instead of losing it
   * to truncation (a 32x32 world earns 0.39 events/tick).
   */
  private float pendingGrowthEvents;

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

    // growth budget scales with world area so food DENSITY, not food total, stays constant
    pendingGrowthEvents += worldSize * GROWTH_EVENTS_PER_TILE;
    int growthEvents = (int) pendingGrowthEvents;
    pendingGrowthEvents -= growthEvents;

    // select N random indexes, if can grow, update quantity
    for (int i = 0; i < growthEvents; i++) {
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
    int centerX = idx % width;
    int centerY = idx / width;
    for (int dx = -meadowRadius; dx <= meadowRadius; dx++) {
      for (int dy = -meadowRadius; dy <= meadowRadius; dy++) {
        if (dx * dx + dy * dy > meadowRadius * meadowRadius) {
          continue;
        }
        int nx = centerX + dx;
        int ny = centerY + dy;
        if (!inBounds(nx, ny, width, height)) {
          continue;
        }
        int neighborIdx = ny * width + nx;
        if (cannotGrowHere(terrainCodec.decode(terrain[neighborIdx]))) {
          continue;
        }
        growPlant(biomass, terrain, neighborIdx, width, height);
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
