package com.ntros.core.ecs.system;

import static com.ntros.core.ecs.data.CreatureType.FOX;
import static com.ntros.core.ecs.data.CreatureType.RABBIT;
import static com.ntros.core.ecs.data.Motive.*;
import static com.ntros.core.ecs.system.TickSystemHelper.*;

import com.ntros.core.ecs.data.Occupancy;
import com.ntros.core.world.DangerGrid;
import com.ntros.core.world.World;

/**
 * The first Agent-related system in the ticking order(Actor order-list). It is the eyes and the
 * brain: senses the surroundings and writes intents (direction + motive) for MovementSystem to
 * execute. Never moves anyone, never changes energy.
 */
public class BehaviorSystem extends AbstractTickSystem {
  public BehaviorSystem(long seed) {
    super(seed);
  }

  @Override
  public void update(World world, long tick) {
    var store = world.getCreatureStore();
    var alive = store.getAlive();

    // Always clear intents at tick START. Clearing at the END(LifecycleSystem)
    // wipes them from the CreatureStore BEFORE its snapshot is taken, meaning the
    // UI is not reporting the correct intents.
    store.clearIntents();

    for (int id = alive.nextSetBit(0); id >= 0; id = alive.nextSetBit(id + 1)) {
      // Priority ladder: danger > food/mate. The ladder must SHORT-CIRCUIT — the highest
      // priority wins by stopping the evaluation. Previously flee was written first and then
      // overwritten by the eat/mate scan, so a hungry rabbit with a fox bearing down chose
      // the berries: written-first had silently become lowest-priority.
      if (isHerbivore(store.species()[id]) && wantsToFlee(world, id)) {
        continue;
      }
      // per-species threshold from the species table: foxes need a bigger surplus than rabbits
      // before mating outranks eating
      if (store.energy()[id] > getCreatureType(store.species()[id]).reproductionThreshold()) {
        wantsToMate(world, id);
      } else {
        wantsToEat(world, id);
      }
    }
  }

  /**
   * @return true when a predator was sensed and a flee intent was written
   */
  private boolean wantsToFlee(World world, int id) {
    int x = (int) world.getCreatureStore().x()[id];
    int y = (int) world.getCreatureStore().y()[id];

    // One array read instead of a full (2r+1)^2 vision scan. The scan never exited early in the
    // common case — no fox nearby means every tile of the disc gets visited — so this was the
    // single most expensive thing in the tick at high rabbit counts. DangerGridSystem stamped the
    // answer for us; the stored value is still the direction TOWARD the predator, which
    // MovementSystem inverts for FLEE.
    byte dangerDirection = world.getDangerGrid().dangerDirectionAt(x, y);
    if (dangerDirection == DangerGrid.NO_DANGER) {
      return false;
    }
    world.getCreatureStore().intentDir()[id] = dangerDirection;
    world.getCreatureStore().intentMotive()[id] = (byte) FLEE.ordinal();
    return true;
  }

  private void wantsToMate(World world, int id) {
    int x = (int) world.getCreatureStore().x()[id];
    int y = (int) world.getCreatureStore().y()[id];
    var closest =
        findClosestCreature(
            world.getCreatureStore(),
            x,
            y,
            world.getWidth(),
            world.getHeight(),
            world.getCreatureStore().species()[id],
            world.getCreatureStore().species()[id]);
    if (isSomeoneThere(closest)) {
      world.getCreatureStore().intentDir()[id] =
          (byte) determineDirection(x, y, closest.x(), closest.y()).ordinal();
      world.getCreatureStore().intentMotive()[id] = (byte) SEEK_MATE.ordinal();
    }
  }

  private void wantsToEat(World world, int id) {
    int x = (int) world.getCreatureStore().x()[id];
    int y = (int) world.getCreatureStore().y()[id];
    var energySourcePos =
        findClosestEnergySource(world, world.getCreatureStore().species()[id], x, y);
    // canExist for biomass
    if (energySourcePos.canExist()) {
      world.getCreatureStore().intentDir()[id] =
          (byte) determineDirection(x, y, energySourcePos.x(), energySourcePos.y()).ordinal();
      // a fox moving on prey is HUNTing (higher urgency than grazing).The FLEE-vs-HUNT
      // urgency race in Motive assumes this distinction exists
      byte motive =
          isHerbivore(world.getCreatureStore().species()[id])
              ? (byte) SEEK_FOOD.ordinal()
              : (byte) HUNT.ordinal();
      world.getCreatureStore().intentMotive()[id] = motive;
    }
  }

  private boolean isSomeoneThere(Occupancy occupancy) {
    return occupancy.id() > -1 && occupancy.x() > -1 && occupancy.y() > -1 && occupancy.canExist();
  }
}
