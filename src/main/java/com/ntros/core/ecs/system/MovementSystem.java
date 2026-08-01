package com.ntros.core.ecs.system;

import static com.ntros.core.ecs.store.CreatureType.RABBIT;
import static com.ntros.core.ecs.system.TickSystemHelper.inBounds;
import static com.ntros.core.ecs.system.TickSystemHelper.isWalkable;
import static com.ntros.core.world.terrain.Tile.*;

import com.ntros.core.ecs.store.CreatureType;
import com.ntros.core.world.World;
import com.ntros.core.world.terrain.Tile;
import java.util.List;

public class MovementSystem extends AbstractTickSystem {

  private static final List<CreatureType> CREATURE_TYPES = List.of(CreatureType.values());
  private static final float RABBIT_STEP_CHANCE = 0.07f;
  private static final float FOX_STEP_CHANCE = 0.12f;
  private static final float MOVE_COST = 0.01f;
  private static final float SMOOTH_STEP_CAP = 0.99f;

  public MovementSystem(long seed) {
    super(seed);
  }

  // just do a random walk, one step per creature. Moving costs energy. How much energy depends on
  // the terrain
  @Override
  public void update(World world, long tick) {
    var creatures = world.getCreatureStore();
    var terrain = world.getTerrain();
    int width = world.getWidth();
    int height = world.getHeight();

    var liveOnes = creatures.getAlive();

    for (int id = liveOnes.nextSetBit(0); id >= 0; id = liveOnes.nextSetBit(id + 1)) {
      if (!canStep(id, creatures.species())) {
        continue;
      }

      // choose dir
      int dx = rng.nextInt(3) - 1;
      int dy = rng.nextInt(3) - 1;

      // look up if next step is possible
      int nx = (int) creatures.x()[id] + dx;
      int ny = (int) creatures.y()[id] + dy;
      if (!inBounds(nx, ny, width, height)) {
        continue;
      }

      // lookup the tile
      Tile tile = terrainCodec.decode(terrain[ny * width + nx]);
      if (!isWalkable(tile)) {
        continue;
      }
      // apply difficulty based on tile
      float difficultyMod = getDifficultTerrainMod(tile);
      // update pos and energy drain
      creatures.x()[id] += dx;
      creatures.y()[id] += dy;
      creatures.energy()[id] -= MOVE_COST + difficultyMod;
    }
  }

  // TODO: implement smooth steps later
  private float smoothModifier(int v) {
    if (v < 0) {
      return v - rng.nextFloat(SMOOTH_STEP_CAP);
    }
    return v + rng.nextFloat(SMOOTH_STEP_CAP);
  }

  private boolean canStep(int id, byte[] species) {
    CreatureType creatureType = CREATURE_TYPES.get(species[id]);
    if (creatureType == RABBIT) {
      return rng.nextFloat() <= RABBIT_STEP_CHANCE;
    }
    return rng.nextFloat() <= FOX_STEP_CHANCE;
  }

  private float getDifficultTerrainMod(Tile tile) {
    if (tile == HILL) {
      return 0.02f;
    }
    if (tile == MOUNTAIN) {
      return 0.07f;
    }
    return 0.00f;
  }

}
