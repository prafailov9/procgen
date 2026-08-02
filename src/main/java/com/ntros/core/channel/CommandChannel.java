package com.ntros.core.channel;

import com.ntros.core.channel.queue.LinkedQueue;
import com.ntros.core.command.Command;

public class CommandChannel extends AbstractChannel<Command> {

  public CommandChannel(int capacity) {
    super(capacity);
  }

  @Override
  public boolean tryOffer(Command command) {
    synchronized (lock) {
      while (queue.size() == capacity) {
        try {
          lock.wait();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
      queue.add(command);
      lock.notifyAll();
      return true;
    }
  }

  @Override
  public void forceOffer(Command command) {
    synchronized (lock) {
      queue.add(command);
      lock.notifyAll();
    }
  }

  @Override
  public Command poll() {
    synchronized (lock) {
      Command command = queue.remove();
      if (command != null) {
        lock.notifyAll(); // wake producers blocked on a full queue
      }
      return command;
    }
  }
}
