package com.ntros.core.clock;

/**
 * Used by the internal sim implementation. A logical clock that manages a single counter value,
 * incrementing once per world update or jumping to a specific time
 */
public interface TickingClock extends Clock {

    /** increments current time by 1 logical timestep */
    void tick();

    /**
     * set current time to given t
     *
     * @param t given time to set the current time counter to. Throws IllegalArgumentException on
     *     given t < 0
     */
    void jump(long t);
}