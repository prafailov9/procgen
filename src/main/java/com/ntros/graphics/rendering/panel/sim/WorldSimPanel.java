package com.ntros.graphics.rendering.panel.sim;

import static com.ntros.graphics.rendering.panel.sim.WorldColorsUtils.*;
import static com.ntros.graphics.rendering.panel.sim.WorldSimConstants.*;

import com.ntros.core.world.WorldSnapshot;
import com.ntros.graphics.rendering.panel.AbstractScreenPanel;
import com.ntros.graphics.rendering.panel.ScreenController;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/** ESC - pause sim, display PAUSE_MENU [1 - 5] - Speed change commands. */
public class WorldSimPanel extends AbstractScreenPanel {

  // pixels per tile
  private int tileSize = MIN_TILE_SIZE;
  // latest snapshot received from the sim; only ever replaced, never mutated
  private WorldSnapshot snapshot;
  // prebuilt rendering image of the snapshot
  private BufferedImage cachedImage;
  // reused pixel buffer for image rebuilds
  private int[] pixelBuffer;

  public WorldSimPanel(ScreenController screenController) {
    super(screenController);
    WorldSimMouseHandler worldSimMouseHandler = new WorldSimMouseHandler(tileSize, this);
    MouseAdapter mouseAdapter = worldSimMouseHandler.getMouseAdapter();
    addMouseWheelListener(mouseAdapter);
    addMouseListener(mouseAdapter);
    addMouseMotionListener(mouseAdapter);
    setFocusable(true);
    setBackground(Color.BLACK);
    // CardLayout fires componentShown when this card becomes the visible one; key listeners only
    // receive events while the panel holds keyboard focus, so grab it on every switch
    addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentShown(ComponentEvent e) {
            requestFocusInWindow();
          }
        });
  }

  /** Accepts the latest published snapshot. Must be called on the EDT. */
  public void present(WorldSnapshot next) {
    if (next == null || next == snapshot) {
      return; // nothing new to show
    }
    snapshot = next;
    rebuildImage(next);
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (cachedImage == null) {
      return;
    }

    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

    g2.drawImage(
        cachedImage,
        0,
        0,
        cachedImage.getWidth() * tileSize,
        cachedImage.getHeight() * tileSize,
        null);
    g2.dispose();
  }

  //  @Override
  //  public void mouseWheelMoved(MouseWheelEvent e) {
  //
  //    // wheel up (negative rotation) zooms in
  //    int next = tileSize - e.getWheelRotation() * ZOOM_STEP;
  //    int clamped = Math.max(MIN_TILE_SIZE, Math.min(MAX_TILE_SIZE, next));
  //    if (clamped != tileSize) {
  //      tileSize = clamped;
  //      revalidate();
  //      repaint();
  //    }
  //  }

  @Override
  public Dimension getPreferredSize() {
    if (snapshot == null) {
      return super.getPreferredSize();
    }
    return new Dimension(snapshot.width() * tileSize, snapshot.height() * tileSize);
  }

  private void rebuildImage(WorldSnapshot snapshot) {
    int width = snapshot.width();
    int height = snapshot.height();

    if (cachedImage == null
        || cachedImage.getWidth() != width
        || cachedImage.getHeight() != height) {
      cachedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      pixelBuffer = new int[width * height];
    }

    byte[] terrain = snapshot.terrain();
    for (int i = 0; i < terrain.length; i++) {
      pixelBuffer[i] = TILE_RGB[terrain[i]];
    }
    cachedImage.setRGB(0, 0, width, height, pixelBuffer, 0, width);
  }
}
