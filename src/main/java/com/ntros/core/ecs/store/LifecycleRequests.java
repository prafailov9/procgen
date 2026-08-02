package com.ntros.core.ecs.store;

import static com.ntros.AppConstants.CREATURES_MAX_CAPACITY;

/**
 * Deferred birth and death command queues: systems write requests during a tick, LifecycleSystem
 * drains them at tick end. This is tick-phase machinery, not storage — kept out of CreatureStore
 * so the store stays arrays + allocation.
 */
public final class LifecycleRequests {

  private int killIdx = 0;
  private final int[] requestedKills = new int[CREATURES_MAX_CAPACITY];

  private int spawnIdx = 0;
  private final int[] spawnPosX = new int[CREATURES_MAX_CAPACITY];
  private final int[] spawnPosY = new int[CREATURES_MAX_CAPACITY];
  private final float[] spawnEnergy = new float[CREATURES_MAX_CAPACITY];
  private final byte[] spawnSpecies = new byte[CREATURES_MAX_CAPACITY];

  // requests kill; applied by LifecycleSystem at end of each tick
  public void shoot(int creatureId) {
    if (killIdx < requestedKills.length) {
      requestedKills[killIdx++] = creatureId;
    }
    // a dropped request self-heals: the creature is shot again next tick
  }

  // requests birth; applied by LifecycleSystem at end of tick. Children must carry their species —
  // without it a newborn inherits whatever byte the previous occupant of its slot left behind.
  public void root(int x, int y, float energy, byte species) {
    if (spawnIdx < spawnPosX.length) {
      spawnPosX[spawnIdx] = x;
      spawnPosY[spawnIdx] = y;
      spawnEnergy[spawnIdx] = energy;
      spawnSpecies[spawnIdx] = species;
      spawnIdx++;
    }
  }

  public int killRequestCount() {
    return killIdx;
  }

  public int[] getRequestedKills() {
    return requestedKills;
  }

  public void clearKillRequests() {
    killIdx = 0;
  }

  public int spawnRequestCount() {
    return spawnIdx;
  }

  public int[] getSpawnPosX() {
    return spawnPosX;
  }

  public int[] getSpawnPosY() {
    return spawnPosY;
  }

  public float[] getSpawnEnergy() {
    return spawnEnergy;
  }

  public byte[] getSpawnSpecies() {
    return spawnSpecies;
  }

  public void clearSpawnRequests() {
    spawnIdx = 0;
  }
}
