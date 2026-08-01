package com.ntros.graphics.rendering;

import com.ntros.core.world.snapshot.WorldSnapshot;
import com.ntros.graphics.rendering.panel.sim.WorldSimPanel;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Presents the latest published world snapshot to the sim panel. Driven by a Swing Timer, so it
 * always runs on the EDT.
 */
public class StateUIRenderer implements Runnable {

  private final WorldSimPanel worldSimPanel;
  private final AtomicReference<WorldSnapshot> latestSnapshot;

  public StateUIRenderer(WorldSimPanel worldSimPanel, AtomicReference<WorldSnapshot> latestSnapshot) {
    this.worldSimPanel = worldSimPanel;
    this.latestSnapshot = latestSnapshot;
  }

  @Override
  public void run() {
    worldSimPanel.present(latestSnapshot.get());
  }
}
