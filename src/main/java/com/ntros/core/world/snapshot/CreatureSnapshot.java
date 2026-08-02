package com.ntros.core.world.snapshot;

/**
 * Immutable copy of the creature state at publish time. Motives are included so the HUD can
 * report what the population is doing (feeding/fleeing/hunting counts) — the observable
 * replacement for per-creature logging in the hot loop.
 */
public record CreatureSnapshot(
    int[] aliveIds,
    float[] x,
    float[] y,
    float[] energy,
    short[] age,
    byte[] species,
    byte[] motives) {}
