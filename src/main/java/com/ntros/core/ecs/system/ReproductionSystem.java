package com.ntros.core.ecs.system;

import com.ntros.core.ecs.data.CreatureType;
import com.ntros.core.ecs.store.CreatureStore;
import com.ntros.core.world.World;

import static com.ntros.core.ecs.system.TickSystemHelper.findNeighborOfSpecies;
import static com.ntros.core.ecs.system.TickSystemHelper.getCreatureType;

public class ReproductionSystem extends AbstractTickSystem {

  public ReproductionSystem(long seed) {
    super(seed);
  }

  // Scan live creatures: a SAME-SPECIES neighbor pair, both with surplus energy, may request
  // offspring. Both parents pay; the child receives exactly what was paid — no free energy.
  // All parameters come from the species table: one shared chance/threshold/cost is how foxes
  // ended up breeding at rabbit rates and overshooting to 11K while prey went extinct.
  @Override
  public void update(World world, long tick) {
    CreatureStore store = world.getCreatureStore();
    var aliveCreatures = store.getAlive();
    for (int id = aliveCreatures.nextSetBit(0); id >= 0; id = aliveCreatures.nextSetBit(id + 1)) {
      byte species = store.species()[id];
      CreatureType type = getCreatureType(species);

      // cheap checks first, before any neighbor scanning
      if (store.energy()[id] < type.reproductionThreshold()) {
        continue;
      }
      // per-tick gate; without it a well-fed pair births every ~6 ticks and the store hits
      // capacity within seconds
      if (rng.nextFloat() >= type.reproductionChance()) {
        continue;
      }

      var mate =
          findNeighborOfSpecies(rng, store, id, world.getWidth(), world.getHeight(), species);
      if (!mate.canExist()) {
        continue;
      }
      if (store.energy()[mate.id()] < type.reproductionThreshold()) {
        continue;
      }

      // spawn on a non-occupied walkable tile
      var spawnPlace = TickSystemHelper.findFreeWalkableSpace(rng, world, id);
      if (spawnPlace.canExist() && spawnPlace.id() < 0) {
        world
            .getLifecycleRequests()
            .root(spawnPlace.x(), spawnPlace.y(), type.reproductionCost() * 2, species);
        store.energy()[id] -= type.reproductionCost();
        store.energy()[mate.id()] -= type.reproductionCost();
      }
    }
  }
}
