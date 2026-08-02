package com.ntros.core.ecs.system;

import com.ntros.core.ecs.data.DeathCause;
import com.ntros.core.ecs.store.CreatureStore;
import com.ntros.core.ecs.store.LifecycleRequests;
import com.ntros.core.world.World;
import com.ntros.core.world.WorldStats;

import java.util.BitSet;

import static com.ntros.AppConstants.CREATURE_START_AGE;

/**
 * The undertaker and the midwife: the only system that actually kills or spawns. Drains the kill
 * and spawn requests other systems queued this tick — O(deaths + births), not O(capacity).
 */
public class LifecycleSystem extends AbstractTickSystem {

  private static final float NIL_FLOAT = 0.0000000f;
  // cached so attribution does not call values() (which clones) once per death
  private static final DeathCause[] DEATH_CAUSES = DeathCause.values();

  public LifecycleSystem(long seed) {
    super(seed);
  }

  @Override
  public void update(World world, long tick) {
    var store = world.getCreatureStore();
    var requests = world.getLifecycleRequests();
    var alive = store.getAlive();

    // flows are tallied here rather than at the call sites because this is the only place that
    // knows which requests actually took effect
    var stats = world.getWorldStats();

    cullMarked(requests, alive, store, stats);
    giveLife(requests, store, stats);
  }

  private void giveLife(LifecycleRequests requests, CreatureStore store, WorldStats stats) {
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
      stats.recordBirth(spawnSpecies[i]);
    }
    requests.clearSpawnRequests();
  }

  private void cullMarked(
      LifecycleRequests requests, BitSet alive, CreatureStore store, WorldStats stats) {
    int[] requestedKills = requests.getRequestedKills();
    byte[] killCauses = requests.getKillCauses();
    int killCount = requests.killRequestCount();
    for (int i = 0; i < killCount; i++) {
      int id = requestedKills[i];
      // the same creature can be requested twice in one tick (starved AND eaten)
      // the first request wins, to prevent the freelist frm receiving the id twice
      if (!alive.get(id)) {
        continue;
      }
      // attribute BEFORE the kill: species is cleared along with the rest of the slot
      stats.recordDeath(store.species()[id], DEATH_CAUSES[killCauses[i]]);

      store.kill(id);
      store.x()[id] = NIL_FLOAT;
      store.y()[id] = NIL_FLOAT;
      store.energy()[id] = NIL_FLOAT;
      store.age()[id] = 0;
    }
    requests.clearKillRequests();
  }
}
