package com.ntros.core.ecs.system;

public enum TickSystemType {
  BIOMASS_SYSTEM(1),
  SPATIAL_INDEX_SYSTEM(2),
  BEHAVIOR_SYSTEM(3),
  MOVEMENT_SYSTEM(4),
  FEEDING_SYSTEM(5),
  METABOLISM_SYSTEM(6),
  REPRODUCTION_SYSTEM(7),
  LIFECYCLE_SYSTEM(8);

  private final int tickingOrder;

  TickSystemType(int tickingOrder) {
    this.tickingOrder = tickingOrder;
  }

  public int getTickingOrder() {
    return tickingOrder;
  }
}
