package com.ntros.core.ecs.data;

public record Occupancy(int id, int x, int y, boolean canExist) {

  // nothing usable found
  public static Occupancy ofNothing() {
    return new Occupancy(-1, -1, -1, false);
  }

  // free tile a creature can occupy
  public static Occupancy ofFree(int x, int y) {
    return new Occupancy(-1, x, y, true);
  }

  // occupied by creature `id`
  public static Occupancy ofTaken(int id, int x, int y) {
    return new Occupancy(id, x, y, true);
  }
}
