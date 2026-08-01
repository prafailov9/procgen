package com.ntros.core.ecs.store;

import java.util.BitSet;

import static com.ntros.AppConstants.CREATURES_CAPACITY;

/**
 * SoA storage for creatures: component arrays plus slot allocation (freelist + alive bitset).
 * Deferred birth/death queues live in {@link LifecycleRequests}.
 */
public class CreatureStore {

  // on spawn: set bits and fields, allocate on the freelist
  // on kill: clear idx on freelist, flip bit, clear arrays
  private final BitSet alive = new BitSet(CREATURES_CAPACITY);
  private int freeCount;

  private final int[] freeList = new int[CREATURES_CAPACITY];

  // all indexed by creatureId
  final float[] x = new float[CREATURES_CAPACITY];
  final float[] y =
      new float[CREATURES_CAPACITY]; // position (float: smooth movement, casts to tile via (int))
  final float[] energy = new float[CREATURES_CAPACITY]; // life budget; 0 = starved
  final short[] age = new short[CREATURES_CAPACITY];
  final byte[] species =
      new byte[CREATURES_CAPACITY]; // index into a Species table of tuning constants

  public CreatureStore() {
    freeCount = CREATURES_CAPACITY;

    // Reverse order so popping from the end allocates IDs 0, 1, 2, ...
    for (int i = 0; i < CREATURES_CAPACITY; i++) {
      freeList[i] = CREATURES_CAPACITY - 1 - i;
    }
  }

  public int spawn() {
    if (freeCount == 0) {
      return -1;
    }
    int creatureId = freeList[--freeCount];
    alive.set(creatureId);
    return creatureId;
  }

  /**
   * @return id of a living creature standing on tile (cx, cy), or -1. Linear scan over alive ids —
   *     replace with a per-tile occupancy grid when populations grow.
   */
  public int creatureAt(int cx, int cy) {
    for (int id = alive.nextSetBit(0); id >= 0; id = alive.nextSetBit(id + 1)) {
      if ((int) x[id] == cx && (int) y[id] == cy) {
        return id;
      }
    }
    return -1;
  }

  public void kill(int creatureId) {
    alive.clear(creatureId);
    freeList[freeCount++] = creatureId;
  }

  public BitSet getAlive() {
    return alive;
  }

  public int[] getPrimitiveAlive() {
    return alive.stream().toArray();
  }

  public float[] energy() {
    return energy;
  }

  public short[] age() {
    return age;
  }

  public byte[] species() {
    return species;
  }

  public float[] x() {
    return x;
  }

  public float[] y() {
    return y;
  }
}
