package com.ntros;

import java.util.concurrent.atomic.AtomicInteger;

public final class IdSequencer {

  private static final AtomicInteger NEXT_COMMAND_ID = new AtomicInteger(1);
  private static final AtomicInteger NEXT_ENTITY_ID = new AtomicInteger(1);

  public static int getNextCommandId() {
    return NEXT_COMMAND_ID.getAndIncrement();
  }

  public static int getNextEntityId() {
    return NEXT_COMMAND_ID.getAndIncrement();
  }
}
