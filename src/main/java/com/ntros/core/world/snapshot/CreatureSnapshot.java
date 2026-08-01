package com.ntros.core.world.snapshot;

public record CreatureSnapshot(
    int[] aliveIds, float[] x, float[] y, float[] energy, short[] age, byte[] species) {}
