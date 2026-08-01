package com.ntros.core.ecs.system;

import com.ntros.core.ecs.store.Occupancy;
import com.ntros.core.ecs.store.CreatureStore;
import com.ntros.core.world.World;

import static com.ntros.AppConstants.CREATURE_MAX_ENERGY;
import static com.ntros.core.ecs.system.TickSystemHelper.NIL_FLOAT;

public class FeedingSystem extends AbstractTickSystem {
  private static final int ENERGY = 5;
  private static final Occupancy NO_NEIGHBOR = Occupancy.ofForbidden();

  public FeedingSystem(long seed) {
    super(seed);
  }

  @Override
  public void update(World world, long tick) {
    int width = world.getWidth();
    int height = world.getHeight();
    var creatureStore = world.getCreatureStore();
    var aliveCreatures = creatureStore.getAlive();

    for (int id = aliveCreatures.nextSetBit(0); id >= 0; id = aliveCreatures.nextSetBit(id + 1)) {
      byte species = creatureStore.species()[id];
      if (TickSystemHelper.isHerbivore(species)) {
        rabbitEats(id, world);
      } else {
        // eat your neighbor
        Occupancy neighbor =
            TickSystemHelper.findNeighborHerbivore(rng, creatureStore, id, width, height);
        if (neighbor.equals(NO_NEIGHBOR)) {
          continue;
        }
        foxEats(world, id, neighbor.id());
      }
    }
  }

  private void rabbitEats(int id, World world) {
    int posX = (int) world.getCreatureStore().x()[id];
    int posY = (int) world.getCreatureStore().y()[id];
    // eat at current pos
    int bioIdx = posY * world.getWidth() + posX;
    // check if food at current pos
    if (world.getBiomass()[bioIdx] > 0) {
      // do no exceed max energy
      if (world.getCreatureStore().energy()[id] + ENERGY <= CREATURE_MAX_ENERGY) {
        world.getCreatureStore().energy()[id] += ENERGY;
      }
      // take same amount of enery from the biomass
      world.getBiomass()[bioIdx] -= ENERGY;
      if (world.getBiomass()[bioIdx] < 0) {
        world.getBiomass()[bioIdx] = 0;
      }
    }
  }

  private void foxEats(World world, int id, int neighId) {
    var store = world.getCreatureStore();
    if (store.energy()[id] + ENERGY <= CREATURE_MAX_ENERGY) {
      store.energy()[id] += ENERGY;
    }
    if (store.energy()[neighId] - ENERGY <= NIL_FLOAT) {
      store.energy()[neighId] = NIL_FLOAT;
      world.getLifecycleRequests().shoot(neighId);
    } else {
      store.energy()[neighId] -= ENERGY;
    }
  }

  /**
   * TODO: move to a helper class Checks each of the 8 neighbor tiles exactly once, starting from a
   * random one so no direction is favored. Hard-bounded — a fox with no neighbors must not spin the
   * sim.
   */
  //  private Neighbor findEatableNeighbor(CreatureStore creatureStore, int id, int w, int h) {
  //    int x = (int) creatureStore.x()[id];
  //    int y = (int) creatureStore.y()[id];
  //    int start = rng.nextInt(NEIGHBORS);
  //
  //    for (int i = 0; i < NEIGHBORS; i++) {
  //      int n = (start + i) % NEIGHBORS;
  //      int nx = x + neighX[n];
  //      int ny = y + neighY[n];
  //
  //      if (!inBounds(nx, ny, w, h)) {
  //        continue;
  //      }
  //      int nId = creatureStore.creatureAt(nx, ny);
  //      if (nId == -1) {
  //        continue;
  //      }
  //      if (CREATURE_TYPES.get(creatureStore.species()[nId]) == RABBIT) {
  //        return new Neighbor(nId, nx, ny, true);
  //      }
  //    }
  //
  //    return NO_NEIGHBOR;
  //  }

}
