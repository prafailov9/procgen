package com.ntros.core.ecs.system;

import com.ntros.core.world.World;

// TODO: implement
public interface TickSystem {
  void update(World world, long tick);
}
