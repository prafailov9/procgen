package com.ntros.core.ecs.system;

import com.ntros.core.ecs.data.DeathCause;
import com.ntros.core.world.World;

public class MetabolismSystem extends AbstractTickSystem {

  private static final float IDLE_COST = 0.1f;

  public MetabolismSystem(long seed) {
    super(seed);
  }

  // cost of living
  @Override
  public void update(World world, long tick) {
    var creatureStore = world.getCreatureStore();
    var lifecycleRequests = world.getLifecycleRequests();
    var aliveCreatures = creatureStore.getAlive();

    for (int id = aliveCreatures.nextSetBit(0); id >= 0; id = aliveCreatures.nextSetBit(id + 1)) {
      creatureStore.energy()[id] -= IDLE_COST;
      if (creatureStore.energy()[id] <= 0) {
        // request kill
        lifecycleRequests.shoot(id, DeathCause.STARVED);
      }
    }
  }
}
