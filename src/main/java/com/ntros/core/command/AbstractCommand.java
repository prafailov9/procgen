package com.ntros.core.command;

import com.ntros.IdSequencer;

public abstract class AbstractCommand implements Command {

  protected final int commandId;
  protected final String commandName;

  protected AbstractCommand(String commandName) {
    this.commandId = IdSequencer.getNextCommandId();
    this.commandName = commandName;
  }

  @Override
  public int getCommandId() {
    return commandId;
  }

  @Override
  public String getCommandName() {
    return commandName;
  }
}
