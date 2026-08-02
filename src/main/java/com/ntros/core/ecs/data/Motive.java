package com.ntros.core.ecs.data;

/**
 * A creature's desired action, plus its urgency: a multiplier on the species' base step chance.
 * Purposeful creatures move more often than idle ones — without this, a "fleeing" rabbit ambled
 * at the same 7%/tick as a grazing one and every chase ended in death.
 */
public enum Motive {
  WANDER(1.0f),
  SEEK_FOOD(3.0f),

  // FLEE must out-urgency HUNT: in a flat chase the prey has to be able to outrun the predator
  // (rabbit 0.07 x 7 = 0.49 vs fox 0.12 x 4 = 0.48), so foxes win through the ambush head start
  // their longer vision gives them (10 vs 6), not through inevitability.
  FLEE(7.0f),
  HUNT(4.0f),
  SEEK_MATE(2.0f);

  // REST TODO: for energy conservation
  // DRINK TODO: integrate when freshwater is added

  private final float urgency;

  Motive(float urgency) {
    this.urgency = urgency;
  }

  public float urgency() {
    return urgency;
  }
}
