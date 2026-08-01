package com.ntros.core.ecs.store;

import java.util.BitSet;

public interface DataStore {

  int [] freeList();

  // x-axis coordinates for the entity
  float[] x();

  // y-axis coordinates for the entity
  float[] y();


  BitSet alive();
}
