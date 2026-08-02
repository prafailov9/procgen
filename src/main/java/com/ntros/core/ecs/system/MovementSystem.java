package com.ntros.core.ecs.system;

import static com.ntros.core.ecs.system.TickSystemHelper.*;
import static com.ntros.core.world.terrain.Tile.*;

import com.ntros.core.ecs.data.Motive;
import com.ntros.core.world.World;
import com.ntros.core.world.terrain.Tile;

public class MovementSystem extends AbstractTickSystem {

  private static final float MOVE_COST = 0.01f;
  private static final float SMOOTH_STEP_CAP = 0.99f;
  // cap on baseStepChance * urgency — even a panicked creature isn't teleporting
  private static final float MAX_STEP_CHANCE = 0.90f;

  public MovementSystem(long seed) {
    super(seed);
  }

  // Executes intents written by BehaviorSystem: directed steps toward (or away from) targets,
  // random walk otherwise. Moving costs energy; harder terrain costs more.
  @Override
  public void update(World world, long tick) {
    var store = world.getCreatureStore();
    var alive = store.getAlive();

    for (int id = alive.nextSetBit(0); id >= 0; id = alive.nextSetBit(id + 1)) {
      Motive motive = getMotive(store.intentMotive()[id]);

      // urgency scales the step chance: purposeful creatures move more often than idle ones.
      // Without this a "fleeing" rabbit ambled at the same 7%/tick as a grazing one, so every
      // chase ended in a kill.
      if (!canStep(store.species()[id], motive)) {
        continue;
      }

      int dirIdx = store.intentDir()[id];
      // Exclusive branches: act on a valid directed intent OR wander — never both. The old
      // fall-through could act twice per tick, and WANDER fell into an empty switch case, so
      // creatures with no target froze in place instead of exploring for food beyond vision.
      if (motive == Motive.WANDER || !hasQueuedIntent(dirIdx)) {
        wander(world, id);
        continue;
      }

      var dir = getNeighborCoordinates(dirIdx);
      int dx = dir.x();
      int dy = dir.y();
      // Behavior stores the direction TOWARD the sensed target; fleeing means stepping the
      // exact opposite way — without this inversion rabbits sprinted into the fox's mouth
      if (motive == Motive.FLEE) {
        dx = -dx;
        dy = -dy;
      }
      step(world, id, dx, dy);
    }
  }

  // choose a step in a random dir: exploration, the only way to find food beyond vision range
  private void wander(World world, int id) {
    int dx = rng.nextInt(3) - 1;
    int dy = rng.nextInt(3) - 1;

    step(world, id, dx, dy);
  }

  private void step(World world, int id, int dx, int dy) {
    var store = world.getCreatureStore();

    // look up if next step is possible
    int nx = (int) store.x()[id] + dx;
    int ny = (int) store.y()[id] + dy;
    if (!inBounds(nx, ny, world.getWidth(), world.getHeight())) {
      return;
    }

    // lookup the tile
    Tile tile = terrainCodec.decode(world.getTerrain()[ny * world.getWidth() + nx]);
    if (!isWalkable(tile)) {
      return;
    }
    // apply difficulty based on tile
    float difficultyMod = getDifficultTerrainMod(tile);
    // update pos and energy drain
    store.x()[id] += dx;
    store.y()[id] += dy;
    store.energy()[id] -= MOVE_COST + difficultyMod;
  }

  // TODO: implement smooth steps later
  private float smoothModifier(int v) {
    if (v < 0) {
      return v - rng.nextFloat(SMOOTH_STEP_CAP);
    }
    return v + rng.nextFloat(SMOOTH_STEP_CAP);
  }

  private boolean hasQueuedIntent(int idx) {
    return idx >= 0 && idx < NEIGHBOR_COORDINATES_COUNT;
  }

  // step chance = species base * motive urgency, both read from the data tables.
  // No per-creature logging in here: this runs for every creature every tick — the HUD's motive
  // counts carry the same information continuously without dragging tps down.
  private boolean canStep(byte species, Motive motive) {
    float chance = getCreatureType(species).baseStepChance() * motive.urgency();
    return rng.nextFloat() <= Math.min(MAX_STEP_CHANCE, chance);
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
