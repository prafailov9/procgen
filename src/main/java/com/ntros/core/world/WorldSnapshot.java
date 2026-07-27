package com.ntros.core.world;

/**
 * Immutable copy of the world state at a given tick. Produced on the sim thread, consumed on the
 * EDT — safe to read without locking. Terrain bytes are Tile ordinals.
 */
public record WorldSnapshot(int width, int height, byte[] terrain, long tick) {

  public static WorldSnapshot of(World world, long tick) {
    return new WorldSnapshot(
        world.getWidth(), world.getHeight(), world.getTerrain().clone(), tick);
  }
}
