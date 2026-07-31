package com.ntros.core.updater;

import com.ntros.core.world.World;
import com.ntros.ecs.system.TickSystem;
import java.util.List;

public class StateActor implements Actor {
  private final List<TickSystem> tickingSystems;

  public StateActor(List<TickSystem> systems) {
    tickingSystems = systems;
  }

  @Override
  public void act(World world, long tick) {
    for (var s : tickingSystems) {
      s.update(world, tick);
    }
  }
}
