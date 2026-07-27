package com.ntros.core.channel;

import com.ntros.core.channel.queue.LinkedQueue;
import com.ntros.core.channel.queue.Queue;

public class CommandChannel implements Channel {

  private final Queue<Command> commandQueue;
  private final Object lock;
  private final int capacity;

  public CommandChannel(int capacity) {
    if (capacity <= 0) {
      throw new IllegalArgumentException("Channel capacity must be positive");
    }

    this.capacity = capacity;
    commandQueue = new LinkedQueue<>();
    lock = new Object();
  }

  @Override
  public boolean tryOffer(Command command) {
    synchronized (lock) {
      while (commandQueue.size() == capacity) {
        try {
          lock.wait();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
      commandQueue.add(command);
      lock.notifyAll();
      return true;
    }
  }

  @Override
  public void forceOffer(Command command) {
    synchronized (lock) {
      commandQueue.add(command);
      lock.notifyAll();
    }
  }

  @Override
  public Command poll() {
    synchronized (lock) {
      Command command = commandQueue.remove();
      if (command != null) {
        lock.notifyAll(); // wake producers blocked on a full queue
      }
      return command;
    }
  }
}
