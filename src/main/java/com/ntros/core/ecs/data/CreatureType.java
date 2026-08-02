package com.ntros.core.ecs.data;

/**
 * The species table: all per-species tuning in one place, indexed by the species byte (ordinal).
 * Systems read traits from here instead of keeping their own paired constants — previously
 * rabbit/fox values were scattered across four systems, which is how foxes ended up breeding at
 * rabbit rates and exploding to 11K.
 */
public enum CreatureType {
  RABBIT(0.07f, 6, 0.010f, 70.0f, 25.0f, 0.70f),

  // Predator stabilizers: foxes see farther (ambush advantage) but must breed far more rarely
  // than prey — real predator energy conversion is ~10%. Same-rate breeding is what produced
  // the overshoot-collapse: fox boom, prey extinction, fox crash.
  FOX(0.12f, 10, 0.0015f, 85.0f, 35.0f, 0.70f);

  // chance to move per tick before urgency scaling (see Motive.urgency)
  private final float baseStepChance;
  // Chebyshev scan radius for Behavior's senses
  private final int visionRadius;
  // per-tick chance that an eligible pair actually breeds
  private final float reproductionChance;
  // minimum energy BOTH parents need before breeding is considered
  private final float reproductionThreshold;
  // energy each parent pays; the child receives exactly 2x this — no free energy
  private final float reproductionCost;
  // above this fraction of max energy, feeding stops (prevents surplus killing)
  private final float satiationFraction;

  CreatureType(
      float baseStepChance,
      int visionRadius,
      float reproductionChance,
      float reproductionThreshold,
      float reproductionCost,
      float satiationFraction) {
    this.baseStepChance = baseStepChance;
    this.visionRadius = visionRadius;
    this.reproductionChance = reproductionChance;
    this.reproductionThreshold = reproductionThreshold;
    this.reproductionCost = reproductionCost;
    this.satiationFraction = satiationFraction;
  }

  public float baseStepChance() {
    return baseStepChance;
  }

  public int visionRadius() {
    return visionRadius;
  }

  public float reproductionChance() {
    return reproductionChance;
  }

  public float reproductionThreshold() {
    return reproductionThreshold;
  }

  public float reproductionCost() {
    return reproductionCost;
  }

  public float satiationFraction() {
    return satiationFraction;
  }
}
