package com.ntros.core.channel;

import com.ntros.core.command.Command;

public class CommandChannel extends AbstractChannel<Command> {

  public CommandChannel(int capacity) {
    super(capacity);
  }
}
