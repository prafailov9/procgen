package com.ntros.core.ecs.data;

/**
 * The species table: all per-species tuning in one place, indexed by the species byte (ordinal).
 * Systems read traits from here instead of keeping their own paired constants — previously
 * rabbit/fox values were scattered across four systems, which is how foxes ended up breeding at
 * rabbit rates and exploding to 11K.
 */
public enum CreatureType {
  // radius 1 == the 8 adjacent tiles, i.e. the original behaviour: rabbits are dense enough that
  // a neighbour is almost always present, so their dynamics are unchanged
  RABBIT(0.07f, 5, 0.010f, 70.0f, 25.0f, 0.70f, 1),

  // Predator stabilizers: foxes see farther (ambush advantage) but must breed far more rarely
  // than prey — real predator energy conversion is around 10%. Same-rate breeding is what produced
  // the overshoot-collapse: fox boom, prey extinction, fox crash.
  //
  // SATIATION MUST STAY ABOVE THE REPRODUCTION THRESHOLD (last two values: 0.90 * 100 > 70).
  // It used to be satiation 0.70 with threshold 85: FeedingSystem stops a fox hunting once it
  // reaches satiation, so a fox capped at 70 energy could essentially never reach the 85 it
  // needed to breed — only a fox sitting just under the gate that caught a near-full rabbit ever
  // qualified. That inversion, not the reproduction rate, is why fox numbers sat flat near 300
  // however hard reproductionChance was tuned: the chance was multiplying a probability of being
  // eligible that was itself near zero. Rabbits have no satiation gate at all (rabbitEats is
  // ungated, they eat to 100), which is why the same shape of constants lets them breed freely.
  //
  // MATE SEARCH RADIUS is the second fox bottleneck, and at low density it is the binding one.
  // Reproduction is quadratic in density (it takes two), so with mates only found on the 8
  // adjacent tiles a sparse predator cannot find itself: at 300 foxes on a big world the expected
  // adjacent-fox count is 0.0012 versus 0.39 for rabbits — a ~330x gap that no reproductionChance
  // can close, since that knob multiplies an already-near-zero term. Real predators range over a
  // territory to find a mate; 20 tiles models that without letting foxes teleport.
  FOX(0.12f, 10, 0.04f, 70.0f, 35.0f, 0.90f, 20);

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
  // how far a breeding-ready creature will look for a partner. Deliberately separate from
  // visionRadius: seeing prey and ranging for a mate are different distances.
  private final int mateSearchRadius;

  CreatureType(
      float baseStepChance,
      int visionRadius,
      float reproductionChance,
      float reproductionThreshold,
      float reproductionCost,
      float satiationFraction,
      int mateSearchRadius) {
    this.baseStepChance = baseStepChance;
    this.visionRadius = visionRadius;
    this.reproductionChance = reproductionChance;
    this.reproductionThreshold = reproductionThreshold;
    this.reproductionCost = reproductionCost;
    this.satiationFraction = satiationFraction;
    this.mateSearchRadius = mateSearchRadius;
  }

  public int mateSearchRadius() {
    return mateSearchRadius;
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
