package com.ntros.core.ecs.system;

import com.ntros.core.ecs.data.CreatureType;
import com.ntros.core.world.World;

/**
 * Rebuilds the predator influence map that BehaviorSystem reads instead of scanning.
 *
 * <p>Must run after MovementSystem settled positions (i.e. alongside the spatial index) and before
 * BehaviorSystem consumes it. No randomness, so no seed.
 */
public class DangerGridSystem implements TickSystem {

  // stamp radius must equal the prey's vision: it is the range at which a rabbit used to detect a
  // fox for itself. If a second prey species with different vision is ever added, this needs one
  // grid per distinct vision radius.
  private static final int STAMP_RADIUS = CreatureType.RABBIT.visionRadius();
  private static final byte PREDATOR = (byte) CreatureType.FOX.ordinal();

  @Override
  public void update(World world, long tick) {
    world.getDangerGrid().rebuild(world, PREDATOR, STAMP_RADIUS);
  }
}
