package com.ntros.core;

import java.util.concurrent.atomic.AtomicBoolean;

public class CancellationToken {
  private final AtomicBoolean isCancelled = new AtomicBoolean(false);

  public boolean isCancelRequested() {
    return isCancelled.get();
  }

  public void cancel() {
    isCancelled.set(true);
  }
}
