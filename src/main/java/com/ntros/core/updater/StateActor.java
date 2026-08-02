package com.ntros.core.updater;

import com.ntros.core.ecs.data.CreatureType;
import com.ntros.core.ecs.system.*;
import com.ntros.core.world.World;

import java.util.*;

import static com.ntros.core.ecs.data.CreatureType.FOX;
import static com.ntros.core.ecs.system.TickSystemType.*;

public class StateActor implements Actor {
  private final List<TickSystem> tickingSystems;

  public StateActor(List<TickSystem> systems) {
    tickingSystems = systems;
  }

  /**
   * The ecosystem tick order. Single source of truth so the regression test exercises exactly the
   * wiring the app runs — a test that builds its own list would keep passing while production
   * drifted.
   *
   * <p>Order matters: the spatial index must be rebuilt before anything queries neighbors, and
   * Lifecycle runs last because it is the only system allowed to actually kill or spawn. Each
   * system derives a distinct RNG stream from the world seed so no two make correlated random
   * choices.
   */
  public static StateActor ofEcosystem(long seed) {
    return new StateActor(
        List.of(
            new BiomassGrowthSystem(seed),
            new SpatialIndexSystem(),
            new BehaviorSystem(seed ^ 0x3D4F5645L),
            new MovementSystem(seed ^ 0x4D4F5645L),
            new FeedingSystem(seed ^ 0x6D4F5645L),
            new MetabolismSystem(seed ^ 0x5D4F5645L),
            new ReproductionSystem(seed ^ 0x9D4F5645L),
            new LifecycleSystem(seed ^ 0x7D4F5645L)));
  }

  @Override
  public void act(World world, long tick) {
    for (var s : tickingSystems) {
      s.update(world, tick);
    }
  }

  @Override
  public void killAllFoxes(World world) {
    var requests = world.getLifecycleRequests();
    requests.shootAll((byte) FOX.ordinal(), world.getCreatureStore());
  }
}
