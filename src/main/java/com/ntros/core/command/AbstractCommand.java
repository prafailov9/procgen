package com.ntros.core.command;

import com.ntros.IdSequencer;

public abstract class AbstractCommand implements Command {

  protected final int commandId;
  protected final String commandName;
  protected final CommandType commandType;

  protected AbstractCommand(CommandType commandType) {
    this.commandId = IdSequencer.getNextCommandId();
    this.commandName = commandType.name();
    this.commandType = commandType;
  }

  @Override
  public int getCommandId() {
    return commandId;
  }

  @Override
  public String getCommandName() {
    return commandName;
  }

  @Override
  public CommandType getCommandType() {
    return commandType;
  }
}
