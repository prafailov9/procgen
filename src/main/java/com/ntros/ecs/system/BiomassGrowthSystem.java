package com.ntros.ecs.system;

import com.ntros.core.world.World;

import java.util.Random;

import static com.ntros.Main.SYSTEM_SEED;

public class BiomassGrowthSystem implements TickSystem {
  private final Random rng = new Random(SYSTEM_SEED);
  private static final int BASE_GROWTH = 1;

  // TODO: select N random tiles to grow instead of a sweeping update
  @Override
  public void update(World world, long tick) {
    var biomass = world.getBiomass();
    int width = world.getWidth();
    int height = world.getHeight();
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int idx = y * width + x;
        // increase qty by 1 each tick. if a qty reaches max(10 for now), populate a new tile next
        // to i in a random direction
        if (biomass[idx] == 10) {
          int randX = rng.nextInt(2);
          int randY = rng.nextInt(2);

          int nx = x + randX;
          int ny = y + randY;
          if (!inBounds(nx, ny, width, height)) {
            continue;
          }

          biomass[ny * width + nx] = BASE_GROWTH;
        } else {
          biomass[idx] += BASE_GROWTH;
        }
      }
    }
  }

  private boolean inBounds(int x, int y, int w, int h) {
    return x >= 0 && x < w && y >= 0 && y < h;
  }
}
