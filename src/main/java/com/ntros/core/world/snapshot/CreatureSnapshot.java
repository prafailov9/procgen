package com.ntros.core.world.snapshot;

import com.ntros.core.ecs.store.CreatureStore;

/**
 * Immutable copy of the LIVING creatures at publish time, compacted.
 *
 * <p>Every array here has length {@code ids.length} and is indexed 0..n-1 in the same order, with
 * {@code ids[i]} giving the entity id of row i. This used to clone the store's raw component
 * arrays instead, which meant every publish copied CREATURES_MAX_CAPACITY entries — about 16 MB
 * at a capacity of one million — regardless of whether a thousand or a hundred thousand creatures
 * were actually alive. Ten publishes a second made that ~160 MB/s of copying and garbage on the
 * sim thread, which is a cost that scales with a constant rather than with the simulation.
 *
 * <p>{@code ids} is ascending, because the store's alive BitSet is walked in order. Consumers rely
 * on that: correlating two snapshots (for render interpolation) is a two-pointer merge rather than
 * a lookup table.
 *
 * <p>Motives are included so the HUD can report what the population is doing.
 */
public record CreatureSnapshot(
    int[] ids,
    float[] x,
    float[] y,
    float[] energy,
    short[] age,
    byte[] species,
    byte[] motives) {

  public static CreatureSnapshot of(CreatureStore store) {
    var alive = store.getAlive();
    int count = alive.cardinality();

    int[] ids = new int[count];
    float[] x = new float[count];
    float[] y = new float[count];
    float[] energy = new float[count];
    short[] age = new short[count];
    byte[] species = new byte[count];
    byte[] motives = new byte[count];

    int row = 0;
    for (int id = alive.nextSetBit(0); id >= 0; id = alive.nextSetBit(id + 1)) {
      ids[row] = id;
      x[row] = store.x()[id];
      y[row] = store.y()[id];
      energy[row] = store.energy()[id];
      age[row] = store.age()[id];
      species[row] = store.species()[id];
      // motives survive until publish because Behavior clears at tick START, not end
      motives[row] = store.intentMotive()[id];
      row++;
    }

    return new CreatureSnapshot(ids, x, y, energy, age, species, motives);
  }

  public int count() {
    return ids.length;
  }
}
