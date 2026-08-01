package com.ntros.core.ecs.system;

import com.ntros.core.world.World;

public class BehaviorSystem extends AbstractTickSystem {
  public BehaviorSystem(long seed) {
    super(seed);
  }

  @Override
  public void update(World world, long tick) {}

  //  private void lookAround(int id, World world, CreatureStore store) {
  //    int radius = rng.nextInt(2, 5);
  //    for (int dy = -radius; dy <= radius; dy++) {
  //      for (int dx = -radius; dx <= radius; dx++) {
  //
  //        // exclude corners of the square bounding box
  //        if (dx * dx + dy * dy > radius * radius) {
  //          continue;
  //        }
  //        float vx = store.x()[id] + dx;
  //        float vy = store.y()[id] + dy;
  //
  //        CreatureType creatureType = CREATURE_TYPES.get(store.species()[id]);
  //        if (creatureType == RABBIT) {
  //          // if energy low, look for closest energy replenishment(food, drink)
  //        }
  //      }
  //    }
  //  }

}
