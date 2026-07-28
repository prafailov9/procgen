package com.ntros.core.command;

import com.ntros.IdSequencer;

public abstract class AbstractCommand implements Command {

  protected final int commandId = IdSequencer.getNextCommandId();

  @Override
  public int getCommandId() {
    return commandId;
  }
}
