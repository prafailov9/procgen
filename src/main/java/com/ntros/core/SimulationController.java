package com.ntros.core;

import com.ntros.core.channel.Channel;
import com.ntros.core.processor.WorldStateProcessor;
import com.ntros.core.world.WorldStats;
import com.ntros.core.world.snapshot.StatsSnapshot;
import com.ntros.graphics.rendering.StateUIRenderer;
import com.ntros.save.WorldSaver;

import javax.swing.*;

public class SimulationController implements Lifecycle {

  private final Thread stateProcessorThread;
  // using Swing's Timer so the renderer runs on the EDT
  private final Timer rendererTimer;

  private final WorldSaver worldSaver;

  private final CancellationToken token;

  public SimulationController(
      WorldStateProcessor worldStateProcessor,
      StateUIRenderer renderer,
      int renderDelayMs,
      CancellationToken cancellationToken,
      Channel<StatsSnapshot> statsChannel) {
    token = cancellationToken;
    stateProcessorThread = new Thread(worldStateProcessor, "state-proc-1");
    rendererTimer = new Timer(renderDelayMs, _ -> renderer.run());
    worldSaver = new WorldSaver(statsChannel);
  }

  @Override
  public void start() {
    stateProcessorThread.start();
    rendererTimer.start();
    worldSaver.start();
  }

  @Override
  public void stop() throws InterruptedException {
    token.cancel();
    stateProcessorThread.interrupt(); // wake the loop if it's sleeping between frames
    stateProcessorThread.join();
    rendererTimer.stop();
    worldSaver.stop();
  }
}
