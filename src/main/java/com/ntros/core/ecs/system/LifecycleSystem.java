package com.ntros.core.ecs.system;

import com.ntros.core.ecs.store.CreatureStore;
import com.ntros.core.ecs.store.LifecycleRequests;
import com.ntros.core.world.World;

import java.util.BitSet;

import static com.ntros.AppConstants.CREATURE_START_AGE;

/**
 * The undertaker and the midwife: the only system that actually kills or spawns. Drains the kill
 * and spawn requests other systems queued this tick — O(deaths + births), not O(capacity).
 */
public class LifecycleSystem extends AbstractTickSystem {

  private static final float NIL_FLOAT = 0.0000000f;

  public LifecycleSystem(long seed) {
    super(seed);
  }

  @Override
  public void update(World world, long tick) {
    var store = world.getCreatureStore();
    var requests = world.getLifecycleRequests();
    var alive = store.getAlive();

    cullMarked(requests, alive, store);
    giveLife(requests, store);
  }

  private void giveLife(LifecycleRequests requests, CreatureStore store) {
    int spawnCount = requests.spawnRequestCount();
    int[] spawnPosX = requests.getSpawnPosX();
    int[] spawnPosY = requests.getSpawnPosY();
    float[] spawnEnergy = requests.getSpawnEnergy();
    byte[] spawnSpecies = requests.getSpawnSpecies();

    for (int i = 0; i < spawnCount; i++) {
      int spawnedCreatureId = store.spawn();
      if (spawnedCreatureId == -1) {
        break; // world at capacity: remaining litters are lost (parents already paid)
      }
      store.x()[spawnedCreatureId] = spawnPosX[i];
      store.y()[spawnedCreatureId] = spawnPosY[i];
      store.energy()[spawnedCreatureId] = spawnEnergy[i];
      store.species()[spawnedCreatureId] = spawnSpecies[i];
      store.age()[spawnedCreatureId] = CREATURE_START_AGE;
    }
    requests.clearSpawnRequests();
  }

  private void cullMarked(LifecycleRequests requests, BitSet alive, CreatureStore store) {
    int[] requestedKills = requests.getRequestedKills();
    int killCount = requests.killRequestCount();
    for (int i = 0; i < killCount; i++) {
      int id = requestedKills[i];
      // the same creature can be requested twice in one tick (starved AND eaten)
      // the first request wins, to prevent the freelist frm receiving the id twice
      if (!alive.get(id)) {
        continue;
      }
      store.kill(id);
      store.x()[id] = NIL_FLOAT;
      store.y()[id] = NIL_FLOAT;
      store.energy()[id] = NIL_FLOAT;
      store.age()[id] = 0;
    }
    requests.clearKillRequests();
  }
}
