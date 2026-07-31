package com.ntros.ecs.system;

import com.ntros.core.world.World;

// TODO: implement
public interface TickSystem {
  void update(World world, long tick);
}
