package com.ntros.core;

import com.ntros.graphics.rendering.StateRenderer;
import javax.swing.*;

public class SimulationController implements Lifecycle {

  private final Thread stateLoopThread;
  // using Swing's Timer so the renderer runs on the EDT
  private final Timer rendererTimer;

  private final CancellationToken token;

  public SimulationController(
      WorldStateLoop worldStateLoop,
      StateRenderer renderer,
      int renderDelayMs,
      CancellationToken cancellationToken) {
    token = cancellationToken;
    stateLoopThread = new Thread(worldStateLoop, "state-loop-1");
    rendererTimer = new Timer(renderDelayMs, event -> renderer.run());
  }

  @Override
  public void start() {
    stateLoopThread.start();
    rendererTimer.start();
  }

  @Override
  public void stop() throws InterruptedException {
    token.cancel();
    stateLoopThread.interrupt(); // wake the loop if it's sleeping between frames
    stateLoopThread.join();
    rendererTimer.stop();
  }
}
