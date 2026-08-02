package com.ntros.core.channel;

public class ConcurrentChannel<T> extends AbstractChannel<T> {
  public ConcurrentChannel(int capacity) {
    super(capacity);
  }
}
