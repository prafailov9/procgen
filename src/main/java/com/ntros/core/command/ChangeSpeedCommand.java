package com.ntros.core.command;

import com.ntros.core.SimulationSpeed;

public class ChangeSpeedCommand extends AbstractCommand {

  private static final String COMMAND_NAME = "CHANGE_SPEED";

  private final SimulationSpeed speed;

  private ChangeSpeedCommand(String commandName, SimulationSpeed speed) {
    super(commandName);
    this.speed = speed;
  }

  public static ChangeSpeedCommand of(SimulationSpeed speed) {
    if (speed == null) {
      throw new IllegalArgumentException("Speed must not be null");
    }
    return new ChangeSpeedCommand(COMMAND_NAME, speed);
  }

  public SimulationSpeed getSpeed() {
    return speed;
  }
}
