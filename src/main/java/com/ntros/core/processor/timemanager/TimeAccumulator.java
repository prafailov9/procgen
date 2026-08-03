package com.ntros.core.processor.timemanager;

/** Enforces the state-proc to update the state at a fixed frequency: 60 ticks/sec. */
public interface TimeAccumulator {
  long getLastPublishTimeNanos();

  long getLastPublishedTick();

  double getElapsedRealTime();

  /**
   * allocated the unprocessed time, retains remainder after updating is done. Without retaining the
   * remainder, the state-proce will rarely run updates.
   */
  double getTimeBucket();
}
