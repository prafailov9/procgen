package com.ntros.core.channel;

public interface Channel<T> {

  boolean tryOffer(T value);

  void forceOffer(T value);

  /**
   * @return the next command, or null when the channel is empty. Never blocks. The state-proc
   *     drains pending commands and moves on to ticking.
   */
  T poll();

  /**
   * Waits until a value is available and returns it. For consumers that have nothing else to do,
   * polling in a tight loop burns a whole core between arrivals, which on a busy sim is CPU stolen
   * from the thread producing the values in the first place.
   *
   * @throws InterruptedException if the waiting thread is interrupted, so shutdown works
   */
  T take() throws InterruptedException;
}
