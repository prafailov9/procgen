package com.ntros.core.ecs.system;

import com.ntros.core.world.World;

/**
 * Rebuilds the per-tile creature index. Must run after MovementSystem (positions are final for
 * this tick) and before any system that queries neighbors (Feeding, Reproduction). No randomness.
 */
public class SpatialIndexSystem implements TickSystem {

  @Override
  public void update(World world, long tick) {
    world.getCreatureStore().rebuildSpatialIndex(world.getWidth(), world.getHeight());
  }
}
