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
    CreatureSnapshot creatureSnapshot,
    StatsSnapshot stats) {

  public static WorldSnapshot of(World world, long tick) {

    return new WorldSnapshot(
        world.getWidth(),
        world.getHeight(),
        tick,
        // Shared by reference, NOT cloned: terrain is written once by generation and no system
        // ever mutates it, so a copy per publish was 2 MB of pure waste on a big world. If a
        // system ever starts editing terrain (erosion, fire scars, player terraforming) this must
        // go back to .clone() — the renderer would otherwise see edits mid-frame.
        world.getTerrain(),
        // biomass genuinely changes every tick, so this one has to be copied
        world.getBiomass().clone(),
        // compacted to living creatures only
        CreatureSnapshot.of(world.getCreatureStore()),
        // already immutable and already built by AnalyticsSystem. No copying needed
        world.getLatestStats());
  }
}
