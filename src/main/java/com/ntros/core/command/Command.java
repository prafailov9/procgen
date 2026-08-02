package com.ntros.core.command;

public interface Command {

  int getCommandId();

  String getCommandName();

  CommandType getCommandType();
}
