package com.ntros.core.ecs.system;

import com.ntros.core.ecs.data.*;
import com.ntros.core.ecs.store.CreatureStore;
import com.ntros.core.world.World;
import com.ntros.core.world.terrain.Tile;

import java.util.List;
import java.util.Random;

import static com.ntros.core.ecs.data.CreatureType.RABBIT;
import static com.ntros.core.ecs.data.Direction.*;
import static com.ntros.core.world.terrain.Tile.*;
import static com.ntros.core.world.terrain.Tile.FOREST;

public final class TickSystemHelper {
  public static final float NIL_FLOAT = 0.00f;

  private static final List<CreatureType> CREATURE_TYPES = List.of(CreatureType.values());
  private static final List<Motive> MOTIVES = List.of(Motive.values());
  public static final int NEIGHBOR_COORDINATES_COUNT = 8;
  // all 8 neighbor paired positions
  public static final int[] neighX = {0, 1, 1, 1, 0, -1, -1, -1};
  public static final int[] neighY = {1, 1, 0, -1, -1, -1, 0, 1};
  // hard clamp on any species' vision: a full-miss ring scan costs around (2r+1)^2 tiles per
  // creature
  // per tick, so unbounded radii are a tps killer (per-species radii live in the species table)
  public static final int MAX_VISION_RADIUS = 14;

  public static Occupancy findClosestCreature(
      CreatureStore creatureStore,
      int x,
      int y,
      int w,
      int h,
      byte finderSpecies,
      byte targetSpecies) {

    int visionRadius = visionRadiusFor(finderSpecies);

    for (int r = 1; r <= visionRadius; r++) {
      // Top and bottom rows.
      for (int dx = -r; dx <= r; dx++) {
        Occupancy found = matchingCreatureAt(creatureStore, x + dx, y - r, w, h, targetSpecies);
        if (found != null) {
          return found;
        }

        found = matchingCreatureAt(creatureStore, x + dx, y + r, w, h, targetSpecies);
        if (found != null) {
          return found;
        }
      }

      // Left and right sides, excluding the corners.
      for (int dy = -(r - 1); dy <= r - 1; dy++) {
        Occupancy found = matchingCreatureAt(creatureStore, x - r, y + dy, w, h, targetSpecies);
        if (found != null) {
          return found;
        }

        found = matchingCreatureAt(creatureStore, x + r, y + dy, w, h, targetSpecies);
        if (found != null) {
          return found;
        }
      }
    }

    return Occupancy.ofNothing();
  }

  private static Occupancy matchingCreatureAt(
      CreatureStore creatureStore, int x, int y, int w, int h, byte targetSpecies) {

    if (!inBounds(x, y, w, h)) {
      return null;
    }

    int creatureId = creatureStore.creatureAt(x, y);

    if (creatureId != -1 && creatureStore.species()[creatureId] == targetSpecies) {
      return Occupancy.ofTaken(creatureId, x, y);
    }

    return null;
  }

  // per-species vision comes from the species table, clamped by the global cost cap
  private static int visionRadiusFor(byte finderSpecies) {
    return Math.min(CREATURE_TYPES.get(finderSpecies).visionRadius(), MAX_VISION_RADIUS);
  }

  public static Occupancy findClosestEnergySource(World world, byte finderSpecies, int x, int y) {
    boolean finderIsRabbit = RABBIT.equals(CREATURE_TYPES.get(finderSpecies));
    int visionRadius = visionRadiusFor(finderSpecies);

    for (int r = 1; r <= visionRadius; r++) {
      // Top and bottom rows.
      for (int dx = -r; dx <= r; dx++) {
        Occupancy found = energySourceAt(world, finderIsRabbit, x, y, x + dx, y - r);
        if (found != null) {
          return found;
        }

        found = energySourceAt(world, finderIsRabbit, x, y, x + dx, y + r);
        if (found != null) {
          return found;
        }
      }

      // Left and right sides without repeating corners.
      for (int dy = -(r - 1); dy <= r - 1; dy++) {
        Occupancy found = energySourceAt(world, finderIsRabbit, x, y, x - r, y + dy);
        if (found != null) {
          return found;
        }

        found = energySourceAt(world, finderIsRabbit, x, y, x + r, y + dy);
        if (found != null) {
          return found;
        }
      }
    }

    return Occupancy.ofNothing();
  }

  private static Occupancy energySourceAt(
      World world, boolean finderIsRabbit, int originX, int originY, int nx, int ny) {

    int width = world.getWidth();
    int height = world.getHeight();

    if (!inBounds(nx, ny, width, height)) {
      return null;
    }

    if (finderIsRabbit) {
      int biomassIndex = ny * width + nx;

      if (world.getBiomass()[biomassIndex] > NIL_FLOAT) {
        return Occupancy.ofFree(nx, ny);
      }

      return null;
    }

    // Foxes do not eat something on their own tile.
    if (nx == originX && ny == originY) {
      return null;
    }

    CreatureStore creatureStore = world.getCreatureStore();
    int creatureId = creatureStore.creatureAt(nx, ny);

    if (creatureId != -1
        && RABBIT.equals(CREATURE_TYPES.get(creatureStore.species()[creatureId]))) {
      return Occupancy.ofTaken(creatureId, nx, ny);
    }

    return null;
  }

  /**
   * Finds an adjacent creature of the given species. Checks each of the 8 neighbor tiles exactly
   * once, starting from a random one so no direction is favored. Hard-bounded: a creature with no
   * neighbors must not spin the sim.
   */
  public static Occupancy findNeighborOfSpecies(
      Random rng, CreatureStore creatureStore, int id, int w, int h, byte species) {
    int x = (int) creatureStore.x()[id];
    int y = (int) creatureStore.y()[id];
    int start = rng.nextInt(NEIGHBOR_COORDINATES_COUNT);

    for (int i = 0; i < NEIGHBOR_COORDINATES_COUNT; i++) {
      int n = (start + i) % NEIGHBOR_COORDINATES_COUNT;
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

    return Occupancy.ofNothing();
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
    // start index used to select a direction
    // selects a random (x, y) direction to start from
    int start = rng.nextInt(NEIGHBOR_COORDINATES_COUNT);

    for (int i = 0; i < NEIGHBOR_COORDINATES_COUNT; i++) {
      // wrap around to first(x, y) if start is at the last (x, y) point
      int n = (start + i) % NEIGHBOR_COORDINATES_COUNT;
      int nx = x + neighX[n];
      int ny = y + neighY[n];

      if (!inBounds(nx, ny, w, h)) {
        continue;
      }

      // no creature should be occupying the current tile
      int nId = creatureStore.creatureAt(nx, ny);
      if (nId != -1) {
        continue;
      }

      // found free space, check if walkable
      if (isWalkable(world.getTile(nx, ny))) {
        return Occupancy.ofFree(nx, ny);
      }
    }

    return Occupancy.ofNothing();
  }

  public static Position getNeighborCoordinates(int coordIdx) {
    return new Position(neighX[coordIdx], neighY[coordIdx]);
  }

  public static CreatureType getCreatureType(byte species) {
    return CREATURE_TYPES.get(species);
  }

  public static Motive getMotive(byte motive) {
    return MOTIVES.get(motive);
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

  /** computes direction from (x, y) perspective towards nx, ny */
  // Integer.signum(n_coord - coord) produces the same result
  public static Direction determineDirection(int x, int y, int nx, int ny) {
    int dx = x - nx;
    int dy = y - ny;
    // check along y-axis
    if (dy < 0) {
      // somewhere north, from (x, y) perspective. find tilt
      if (dx < 0) {
        return NORTH_EAST;
      } else if (dx > 0) {
        return NORTH_WEST;
      } else {
        return NORTH;
      }
    } else if (dy == 0) {
      // left, right or same pos
      if (dx < 0) {
        return EAST;
      } else if (dx > 0) {
        return WEST;
      } else {
        return NO_DIR;
      }
    }
    // somewhere south
    if (dx < 0) {
      return SOUTH_EAST;
    } else if (dx > 0) {
      return SOUTH_WEST;
    }
    return SOUTH;
  }
}
