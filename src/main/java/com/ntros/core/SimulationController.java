package com.ntros.core;

import com.ntros.core.processor.WorldStateProcessor;
import com.ntros.graphics.rendering.StateUIRenderer;
import javax.swing.*;

public class SimulationController implements Lifecycle {

  private final Thread stateProcessorThread;
  // using Swing's Timer so the renderer runs on the EDT
  private final Timer rendererTimer;

  private final CancellationToken token;

  public SimulationController(
      WorldStateProcessor worldStateProcessor,
      StateUIRenderer renderer,
      int renderDelayMs,
      CancellationToken cancellationToken) {
    token = cancellationToken;
    stateProcessorThread = new Thread(worldStateProcessor, "state-proc-1");
    rendererTimer = new Timer(renderDelayMs, event -> renderer.run());
  }

  @Override
  public void start() {
    stateProcessorThread.start();
    rendererTimer.start();
  }

  @Override
  public void stop() throws InterruptedException {
    token.cancel();
    stateProcessorThread.interrupt(); // wake the loop if it's sleeping between frames
    stateProcessorThread.join();
    rendererTimer.stop();
  }
}
