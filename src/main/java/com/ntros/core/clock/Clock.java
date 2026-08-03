package com.ntros.core.clock;

/** A read-only interface, providing a view of the current wall time. */
public interface Clock {
  // get current logical time value
  long currentTick();

  // calendar units derived from the logical time counter
  int minuteOfDay();

  int hour();

  int minute();

  long day();
}
