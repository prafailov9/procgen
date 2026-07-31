package com.ntros.ecs.store;

import java.util.BitSet;

import static com.ntros.AppConstants.CREATURES_CAPACITY;

public class CreatureStore {

  // on spawn: set bits and fields, allocate on the freelist
  // on kill: clear idx on freelist, flip bit, clear arrays
  private final BitSet alive = new BitSet(CREATURES_CAPACITY);
  private int freeCount;

  /**
   * Spawning: int index = freeList[--freeCount]; alive.set(index);
   *
   * <p>Killing: alive.clear(index); freeList[freeCount++] = index;
   */
  private final int[] freeList = new int[CREATURES_CAPACITY];

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

  public float[] energy() {
    return energy;
  }

  public short[] age() {
    return age;
  }

  public byte[] species() {
    return species;
  }

  public int[] freeList() {
    return freeList;
  }

  public float[] x() {
    return x;
  }

  public float[] y() {
    return y;
  }

  public BitSet alive() {
    return alive;
  }
}
