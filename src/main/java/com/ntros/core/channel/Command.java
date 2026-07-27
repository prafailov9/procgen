package com.ntros.core.channel;

import com.ntros.IdSequencer;

public class Command {

  private final int commandId;
  private final String payload;

  private Command(String payload) {
    this.commandId = IdSequencer.getNextCommandId();
    this.payload = payload;
  }

  public static Command of(String payload) {
    if (payload == null || payload.isBlank()) {
      throw new IllegalArgumentException("Empty command payload");
    }

    return new Command(payload);
  }
}
