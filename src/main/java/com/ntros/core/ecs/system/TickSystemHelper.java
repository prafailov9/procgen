package com.ntros.core.ecs.system;

import com.ntros.core.ecs.store.Occupancy;
import com.ntros.core.ecs.store.CreatureStore;
import com.ntros.core.ecs.store.CreatureType;
import com.ntros.core.world.World;
import com.ntros.core.world.terrain.TerrainCodec;
import com.ntros.core.world.terrain.Tile;

import java.util.List;
import java.util.Random;

import static com.ntros.core.ecs.store.CreatureType.RABBIT;
import static com.ntros.core.world.terrain.Tile.*;
import static com.ntros.core.world.terrain.Tile.FOREST;

public final class TickSystemHelper {
  public static final float NIL_FLOAT = 0.00f;

  // min energy level allowed for reproduction
  public static final float REPRODUCTION_THRESHOLD = 70.0f;
  private static final List<CreatureType> CREATURE_TYPES = List.of(CreatureType.values());
  public static final int NEIGHBORS = 8;
  // all 8 neighbor paired positions
  public static final int[] neighX = {0, 1, 1, 1, 0, -1, -1, -1};
  public static final int[] neighY = {1, 1, 0, -1, -1, -1, 0, 1};

  private static final TerrainCodec CODEC = new TerrainCodec();

  /**
   * Finds an adjacent creature of the given species. Checks each of the 8 neighbor tiles exactly
   * once, starting from a random one so no direction is favored. Hard-bounded — a creature with no
   * neighbors must not spin the sim.
   */
  public static Occupancy findNeighborOfSpecies(
      Random rng, CreatureStore creatureStore, int id, int w, int h, byte species) {
    int x = (int) creatureStore.x()[id];
    int y = (int) creatureStore.y()[id];
    int start = rng.nextInt(NEIGHBORS);

    for (int i = 0; i < NEIGHBORS; i++) {
      int n = (start + i) % NEIGHBORS;
      int nx = x + neighX[n];
      int ny = y + neighY[n];

      if (!inBounds(nx, ny, w, h)) {
        continue;
      }
      int nId = creatureStore.creatureAt(nx, ny);
      if (nId == -1) {
        continue;
      }
      if (creatureStore.species()[nId] == species) {
        return Occupancy.ofTaken(nId, nx, ny);
      }
    }

    return Occupancy.ofForbidden();
  }

  /** Prey search: foxes hunt adjacent rabbits. */
  public static Occupancy findNeighborHerbivore(
      Random rng, CreatureStore creatureStore, int id, int w, int h) {
    return findNeighborOfSpecies(rng, creatureStore, id, w, h, (byte) RABBIT.ordinal());
  }

  public static Occupancy findFreeWalkableSpace(Random rng, World world, int id) {
    var creatureStore = world.getCreatureStore();
    int w = world.getWidth();
    int h = world.getHeight();
    int x = (int) creatureStore.x()[id];
    int y = (int) creatureStore.y()[id];
    int start = rng.nextInt(NEIGHBORS);

    for (int i = 0; i < NEIGHBORS; i++) {
      int n = (start + i) % NEIGHBORS;
      int nx = x + neighX[n];
      int ny = y + neighY[n];

      if (!inBounds(nx, ny, w, h)) {
        continue;
      }
      int nId = creatureStore.creatureAt(nx, ny);
      if (nId != -1) {
        continue;
      }

      // found free space, check if walkable
      if (isWalkable(world.getTile(nx, ny))) {
        return Occupancy.ofFree(nx, ny);
      }
    }

    return Occupancy.ofForbidden();
  }

  public static boolean isHerbivore(byte species) {
    return CREATURE_TYPES.get(species) == RABBIT;
  }

  public static boolean isWalkable(Tile tile) {
    return tile != DEEP_WATER && tile != SHALLOW_WATER;
  }

  public static boolean inBounds(int x, int y, int w, int h) {
    return x >= 0 && x < w && y >= 0 && y < h;
  }

  public static boolean cannotGrowHere(Tile tile) {
    return (tile != GRASS && tile != FOREST);
  }
}
