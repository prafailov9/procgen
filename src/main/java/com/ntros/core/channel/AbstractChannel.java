package com.ntros.core.channel;

import com.ntros.core.channel.queue.LinkedQueue;
import com.ntros.core.channel.queue.Queue;
import com.ntros.core.command.Command;

public abstract class AbstractChannel<T> implements Channel<T> {

  protected final Queue<T> queue;
  protected final Object lock;
  protected final int capacity;

  protected AbstractChannel(int capacity) {
    this.capacity = capacity;
    lock = new Object();
    queue = new LinkedQueue<>();
  }

  @Override
  public boolean tryOffer(T value) {
    synchronized (lock) {
      while (queue.size() >= capacity) {
        try {
          lock.wait();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
      queue.add(value);
      lock.notifyAll();
      return true;
    }
  }

  @Override
  public void forceOffer(T value) {
    synchronized (lock) {
      queue.add(value);
      lock.notifyAll();
    }
  }

  @Override
  public T poll() {
    synchronized (lock) {
      T value = queue.remove();
      if (value != null) {
        lock.notifyAll(); // wake producers blocked on a full queue
      }
      return value;
    }
  }

  @Override
  public T take() throws InterruptedException {
    synchronized (lock) {
      T value;
      // guarded wait: notifyAll can wake us for a producer's sake, and spurious wakeups are
      // permitted, so re-check rather than assuming a value is present
      while ((value = queue.remove()) == null) {
        lock.wait();
      }
      lock.notifyAll(); // wake producers blocked on a full queue
      return value;
    }
  }

}
