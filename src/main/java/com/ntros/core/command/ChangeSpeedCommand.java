package com.ntros.core.command;

import com.ntros.core.SimulationSpeed;

public class ChangeSpeedCommand extends AbstractCommand {
  private final int speedValue;

  private ChangeSpeedCommand(int speedValue) {
    this.speedValue = speedValue;
  }

  public static ChangeSpeedCommand ofPaused() {
    return new ChangeSpeedCommand(SimulationSpeed.PAUSED.getSpeedValue());
  }

  public static ChangeSpeedCommand ofX1() {
    return new ChangeSpeedCommand(SimulationSpeed.X1.getSpeedValue());
  }

  public static ChangeSpeedCommand ofX5() {
    return new ChangeSpeedCommand(SimulationSpeed.X5.getSpeedValue());
  }

  public static ChangeSpeedCommand ofX25() {
    return new ChangeSpeedCommand(SimulationSpeed.X25.getSpeedValue());
  }

  public static ChangeSpeedCommand ofX250() {
    return new ChangeSpeedCommand(SimulationSpeed.X250.getSpeedValue());
  }

  public static ChangeSpeedCommand ofMax() {
    return new ChangeSpeedCommand(SimulationSpeed.MAX.getSpeedValue());
  }

  public int getSpeedValue() {
    return speedValue;
  }
}
