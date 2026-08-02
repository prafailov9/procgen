package com.ntros.core.world.snapshot;

import com.ntros.core.world.World;

/**
 * Immutable copy of the world state at a given tick. Produced on the sim thread, consumed on the
 * EDT - safe to read without locking.
 */
public record WorldSnapshot(
    int width,
    int height,
    long tick,
    byte[] terrain,
    float[] biomass,
    CreatureSnapshot creatureSnapshot) {

  public static WorldSnapshot of(World world, long tick) {

    return new WorldSnapshot(
        world.getWidth(),
        world.getHeight(),
        tick,
        world.getTerrain().clone(),
        world.getBiomass().clone(),
        new CreatureSnapshot(
            world.getCreatureStore().getPrimitiveAlive(),
            world.getCreatureStore().x().clone(),
            world.getCreatureStore().y().clone(),
            world.getCreatureStore().energy().clone(),
            world.getCreatureStore().age().clone(),
            world.getCreatureStore().species().clone(),
            // motives survive until publish because Behavior clears at tick START, not end
            world.getCreatureStore().intentMotive().clone()));
  }
}
