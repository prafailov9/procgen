package com.ntros.core.world;

import com.ntros.core.ecs.data.Direction;
import com.ntros.core.ecs.store.CreatureStore;

import java.util.Arrays;

import static com.ntros.core.ecs.system.TickSystemHelper.determineDirection;
import static com.ntros.core.ecs.system.TickSystemHelper.inBounds;

/**
 * Per-tile "a predator can be seen from here" layer — an influence map.
 *
 * <p>This exists to invert the predator-detection query. Every rabbit used to scan its whole vision
 * disc for foxes every tick, and because the common case is "no fox nearby" the scan never exited
 * early: it paid the full (2r+1)^2 tiles. At 100K rabbits with vision 5 that is 12M creatureAt
 * calls per tick, which is what pinned the big world at ~65 tps.
 *
 * <p>Inverted, the few predators stamp their surroundings once (600 foxes x 121 tiles = ~72K
 * writes) and every rabbit answers "is danger near, and from which way?" with a single array read.
 * The work now scales with the number of PREDATORS rather than the number of prey, which is the
 * right way round: predators are always the rare side of a food chain.
 */
public final class DangerGrid {

  /** No predator within stamping range of this tile. */
  public static final byte NO_DANGER = -1;

  private static final byte UNSTAMPED_DISTANCE = Byte.MAX_VALUE;

  // direction index (a Direction ordinal, 0..7) pointing FROM this tile TOWARD the nearest
  // predator — the same convention BehaviorSystem wrote before, so MovementSystem still inverts
  // it for FLEE
  private byte[] direction;
  // Chebyshev distance to that predator, so overlapping stamps keep the closest one
  private byte[] distance;

  // tiles touched by the last rebuild, so clearing costs O(stamped) instead of O(world)
  private int[] usedCells = new int[0];
  private int usedCount;
  private int width;

  /**
   * Rebuilds the layer from current predator positions.
   *
   * @param stampRadius how far a predator can be sensed must equal the prey's vision radius, since
   *     that is the range the prey used to scan for itself
   */
  public void rebuild(World world, byte predatorSpecies, int stampRadius) {
    int worldWidth = world.getWidth();
    int worldHeight = world.getHeight();
    int size = worldWidth * worldHeight;

    if (direction == null || direction.length != size) {
      direction = new byte[size];
      distance = new byte[size];
      Arrays.fill(direction, NO_DANGER);
      Arrays.fill(distance, UNSTAMPED_DISTANCE);
      usedCount = 0;
    }
    width = worldWidth;

    // clear only what the previous tick stamped
    for (int i = 0; i < usedCount; i++) {
      direction[usedCells[i]] = NO_DANGER;
      distance[usedCells[i]] = UNSTAMPED_DISTANCE;
    }
    usedCount = 0;

    CreatureStore store = world.getCreatureStore();
    var alive = store.getAlive();
    ensureUsedCellsCapacity(store, stampRadius);

    for (int id = alive.nextSetBit(0); id >= 0; id = alive.nextSetBit(id + 1)) {
      if (store.species()[id] != predatorSpecies) {
        continue;
      }
      stamp((int) store.x()[id], (int) store.y()[id], stampRadius, worldWidth, worldHeight);
    }
  }

  private void stamp(int predatorX, int predatorY, int radius, int worldWidth, int worldHeight) {
    for (int dy = -radius; dy <= radius; dy++) {
      for (int dx = -radius; dx <= radius; dx++) {
        // skip the predator's own tile: the ring scan this replaces started at radius 1, and
        // determineDirection of a tile to itself is NO_DIR, which is not a valid step index
        if (dx == 0 && dy == 0) {
          continue;
        }
        int tileX = predatorX + dx;
        int tileY = predatorY + dy;
        if (!inBounds(tileX, tileY, worldWidth, worldHeight)) {
          continue;
        }

        // Chebyshev distance matches the ring search's metric
        int chebyshev = Math.max(Math.abs(dx), Math.abs(dy));
        int cell = tileY * worldWidth + tileX;
        if (chebyshev >= distance[cell]) {
          continue; // an equally close or closer predator already claimed this tile
        }
        if (distance[cell] == UNSTAMPED_DISTANCE && usedCount < usedCells.length) {
          usedCells[usedCount++] = cell;
        }
        distance[cell] = (byte) chebyshev;
        Direction toward = determineDirection(tileX, tileY, predatorX, predatorY);
        direction[cell] = (byte) toward.ordinal();
      }
    }
  }

  /**
   * @return a Direction ordinal pointing toward the nearest predator, or {@link #NO_DANGER}
   */
  public byte dangerDirectionAt(int x, int y) {
    if (direction == null) {
      return NO_DANGER;
    }
    return direction[y * width + x];
  }

  /** Worst case one distinct cell per predator per stamped tile. */
  private void ensureUsedCellsCapacity(CreatureStore store, int stampRadius) {
    int stampedPerPredator = (2 * stampRadius + 1) * (2 * stampRadius + 1);
    int needed = store.getAlive().cardinality() * stampedPerPredator;
    if (usedCells.length < needed) {
      usedCells = new int[Math.max(needed, usedCells.length * 2)];
    }
  }
}
