package com.ntros.core.updater;

import com.ntros.core.world.World;

/** Abstraction to act out the simulation, decouples the executor from the executable action.
 * TODO: World should also be an interface
 * */
public interface Actor {
  void act(World world, long tick);
  void killAllFoxes(World world);
}
