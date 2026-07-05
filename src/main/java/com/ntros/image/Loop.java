package com.ntros.image;

import java.util.concurrent.atomic.AtomicBoolean;

public class Loop {

  private final AtomicBoolean running = new AtomicBoolean(true);

  public void update() {
    long lastLoopTime = System.nanoTime();
    final int TARGET_FPS = 60;
    final long OPTIMAL_TIME = 1000000000 / TARGET_FPS;


    while (running.get()) {

    }


  }

}
