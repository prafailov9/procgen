package com.ntros.core.clock;

public class SimClock implements TickingClock {

    // Default time scale: 1440 ticks per simulated day, so 1 tick = 1 simulated minute
    private static final long MINUTES_PER_DAY = 24 * 60;

    // logical tick counter
    private long time = 0;

    // simulation time scale. Simulation time is advancing at this given rate, compared to real time
    private final long ticksPerDay;

    /// Builders
    private SimClock(long ticksPerDay) {
        if (ticksPerDay <= 0) {
            throw new IllegalArgumentException("Time scale cannot be <= 0");
        }
        this.ticksPerDay = ticksPerDay;
    }

    public static SimClock ofDefaultTimeScale() {
        return new SimClock(MINUTES_PER_DAY);
    }

    public static SimClock ofTicksPerDay(int ticksPerDay) {
        return new SimClock(ticksPerDay);
    }

    @Override
    public long currentTick() {
        return time;
    }

    @Override
    public void tick() {
        time++;
    }

    @Override
    public void jump(long t) {
        if (t > time) {
            time = t;
        }
    }

    @Override
    public int hour() {
        return minuteOfDay() / 60;
    }

    @Override
    public int minute() {
        return minuteOfDay() % 60;
    }

    @Override
    public int minuteOfDay() {
        return (int) ((Math.floorMod(time, ticksPerDay) * MINUTES_PER_DAY) / ticksPerDay);
    }

    @Override
    public long day() {
        return Math.floorDiv(time, ticksPerDay);
    }
}
