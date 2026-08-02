package com.ntros.core.ecs.data;

/**
 * Why a creature died. Carried on the kill request so LifecycleSystem — the only system that
 * actually applies deaths — can attribute each one exactly once.
 *
 * <p>Counting at the source instead would double-count: a rabbit can be marked STARVED by
 * Metabolism and EATEN by Feeding in the same tick, yet only one death occurs.
 *
 * <p>The split matters for tuning. "Foxes are declining" is unactionable; "foxes are declining
 * and 100% of their deaths are STARVED" says the predator is prey-limited, while a low death
 * rate with few births says it is mate-limited. Opposite fixes.
 */
public enum DeathCause {
  STARVED,
  EATEN,
  CULLED
}
