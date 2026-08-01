package com.ntros.core.ecs.system;

import com.ntros.core.world.terrain.TerrainCodec;

import java.util.Random;

public abstract class AbstractTickSystem implements TickSystem {

  protected final Random rng;
  protected final TerrainCodec terrainCodec = new TerrainCodec();

  protected AbstractTickSystem(long seed) {
    rng = new Random(seed);
  }
}
