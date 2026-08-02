package com.ntros.core.ecs.system;

import com.ntros.core.ecs.data.Occupancy;
import com.ntros.core.world.World;

import static com.ntros.AppConstants.CREATURE_MAX_ENERGY;
import static com.ntros.core.ecs.system.TickSystemHelper.*;

public class FeedingSystem extends AbstractTickSystem {
  private static final int ENERGY = 5;
  private static final Occupancy NO_NEIGHBOR = Occupancy.ofNothing();

  private static final float PREY_ENERGY_CONVERSION = 0.1f;

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

      if (isHerbivore(species)) {
        rabbitEats(id, world);
      } else {
        // Satiation gate: 70. A full fox does not hunt. Prevents foxes from non-stop killing.
        float satiationLevel = CREATURE_MAX_ENERGY * getCreatureType(species).satiationFraction();
        if (creatureStore.energy()[id] >= satiationLevel) {
          continue;
        }

        // foxes only eat rabbits, on adjacent tiles
        Occupancy neighbor = findNeighborHerbivore(rng, creatureStore, id, width, height);
        if (neighbor.equals(NO_NEIGHBOR)) {
          continue;
        }
        foxEats(world, id, neighbor.id());
      }
    }
  }

  private void rabbitEats(int id, World world) {
    var store = world.getCreatureStore();
    float[] biomass = world.getBiomass();
    // eat at current pos
    int bioIdx = (int) store.y()[id] * world.getWidth() + (int) store.x()[id];

    if (biomass[bioIdx] > 0) {
      // Take only what can actually be stored: min(bite, room left, what's there). Previously a
      // full rabbit destroyed 5 biomass/tick while gaining nothing. One parked rabbit deleted
      // around 7,200 biomass per sim-day. Gained and consumed are now always equal (conservation).
      float room = CREATURE_MAX_ENERGY - store.energy()[id];
      float take = Math.min(ENERGY, Math.min(room, biomass[bioIdx]));
      if (take > 0) {
        store.energy()[id] += take;
        biomass[bioIdx] -= take;
      }
    }
  }

  private void foxEats(World world, int id, int preyId) {
    var store = world.getCreatureStore();

    // Kill-on-catch: fox kills the rabbit instantly, gaining only a fraction of it's remaining hp,
    // capped by CREATURE_MAX_ENERGY max value.
    // The old 5 dmg/tick DOT effect allowed the fox to drain the rabbit's hp over 20 ticks and
    // convert all of it to its own energy.Now the prey
    // dies once, the fox gains PREY_CONVERSION of its remaining energy (capped by the fox's
    // own maximum), and escape has real value: a rabbit that gets away keeps everything.
    float gain =
        Math.min(
            store.energy()[preyId] * PREY_ENERGY_CONVERSION,
            CREATURE_MAX_ENERGY - store.energy()[id]);
    store.energy()[id] += gain;
    store.energy()[preyId] = NIL_FLOAT;
    world.getLifecycleRequests().shoot(preyId);
  }
}
