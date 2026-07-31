package com.ntros.core.updater;

import com.ntros.core.world.World;

public interface Actor {
  void act(World world, long tick);
}
