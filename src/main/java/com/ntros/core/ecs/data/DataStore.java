package com.ntros.core.ecs.data;

import java.util.BitSet;

public interface DataStore {

  int [] freeList();

  // x-axis coordinates for the entity
  float[] x();

  // y-axis coordinates for the entity
  float[] y();


  BitSet alive();
}
