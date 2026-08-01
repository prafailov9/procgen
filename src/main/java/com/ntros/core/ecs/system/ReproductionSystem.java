package com.ntros.core.ecs.system;

import com.ntros.core.ecs.store.CreatureStore;
import com.ntros.core.world.World;

import static com.ntros.core.ecs.system.TickSystemHelper.REPRODUCTION_THRESHOLD;
import static com.ntros.core.ecs.system.TickSystemHelper.findNeighborOfSpecies;

public class ReproductionSystem extends AbstractTickSystem {

  private static final float REP_ENERGY_COST = 25.0f;
  // chance per tick that an eligible pair actually breeds. Without this gate a well-fed pair
  // births every ~6 ticks and the store hits capacity within seconds.
  private static final float REPRODUCTION_CHANCE = 0.01f;

  public ReproductionSystem(long seed) {
    super(seed);
  }

  // scan live creatures: a SAME-SPECIES neighbor pair, both with surplus energy, may request
  // offspring. Both parents pay; the child receives exactly what was paid — no free energy.
  @Override
  public void update(World world, long tick) {
    CreatureStore store = world.getCreatureStore();
    var aliveCreatures = store.getAlive();
    for (int id = aliveCreatures.nextSetBit(0); id >= 0; id = aliveCreatures.nextSetBit(id + 1)) {
      // cheap checks first, before any neighbor scanning
      if (store.energy()[id] < REPRODUCTION_THRESHOLD) {
        continue;
      }
      if (rng.nextFloat() >= REPRODUCTION_CHANCE) {
        continue;
      }

      byte species = store.species()[id];
      var mate =
          findNeighborOfSpecies(rng, store, id, world.getWidth(), world.getHeight(), species);
      if (!mate.canExist()) {
        continue;
      }
      if (store.energy()[mate.id()] < REPRODUCTION_THRESHOLD) {
        continue;
      }

      // spawn on a non-occupied walkable tile
      var spawnPlace = TickSystemHelper.findFreeWalkableSpace(rng, world, id);
      if (spawnPlace.canExist() && spawnPlace.id() < 0) {
        world
            .getLifecycleRequests()
            .root(spawnPlace.x(), spawnPlace.y(), REP_ENERGY_COST * 2, species);
        store.energy()[id] -= REP_ENERGY_COST;
        store.energy()[mate.id()] -= REP_ENERGY_COST;
      }
    }
  }
}
