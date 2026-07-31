package com.ntros.ecs;

import java.util.BitSet;

public interface DataStore {

    double[] getX();
    double[] getY();
    BitSet getExisting();

}
