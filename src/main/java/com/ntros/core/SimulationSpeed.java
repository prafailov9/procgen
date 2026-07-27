package com.ntros.core;

public enum SimulationSpeed {

    PAUSED(-1), X1(1), X5(5), X25(25), X250(250), MAX(512);
    private final int value;

     SimulationSpeed(int value) {
        this.value = value;
    }

    public int getSpeedValue() {
         return value;
    }

}
