package com.ntros.graphics.rendering.panel.sim;

import static com.ntros.graphics.rendering.panel.sim.WorldColorsUtils.*;

import com.ntros.core.world.WorldSnapshot;
import com.ntros.graphics.rendering.panel.AbstractScreenPanel;
import com.ntros.graphics.rendering.panel.ScreenController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

/**
 * ESC - pause sim, display PAUSE_MENU [1 - 5] - Speed change commands. WASD - pan world by
 * direction
 */
public class WorldSimPanel extends AbstractScreenPanel {

  private static final Logger log = LoggerFactory.getLogger(WorldSimPanel.class);

  private static final double ZOOM_FACTOR = 1.15;
  private static final double MAX_RELATIVE_ZOOM = 74.0;
  private static final double KEYBOARD_PAN_STEP = 74.0;

  // pixels per tile
  // latest snapshot received from the sim; only ever replaced, never mutated
  private WorldSnapshot snapshot;
  // prebuilt rendering image of the snapshot
  private BufferedImage cachedImage;
  // reused pixel buffer for image rebuilds
  private int[] pixelBuffer;
  private double scale = 1.0;
  private double coverScale = 1.0;
  private double panX;
  private double panY;
  private boolean viewInitialized;

  public WorldSimPanel(ScreenController screenController) {
    super(screenController);

    WorldSimMouseHandler worldSimMouseHandler = new WorldSimMouseHandler(this);
    addMouseWheelListener(worldSimMouseHandler);
    addMouseListener(worldSimMouseHandler);
    addMouseMotionListener(worldSimMouseHandler);

    setFocusable(true);
    setBackground(Color.BLACK);

    installPanKeyBindings();

    // CardLayout fires componentShown when this card becomes the visible one; key listeners only
    // receive events while the panel holds keyboard focus, so grab it on every switch
    addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            if (cachedImage != null) {
              fillPanelWithWorld();
            }
          }

          @Override
          public void componentShown(ComponentEvent e) {
            requestFocusInWindow();
            if (!viewInitialized) {
              fillPanelWithWorld();
            }
          }
        });
  }

  /** Accepts the latest published snapshot. Must be called on the EDT. */
  public void present(WorldSnapshot next) {
    if (next == null || next == snapshot) {
      return; // nothing new to show
    }
    boolean dimensionsChanged =
        snapshot == null || snapshot.width() != next.width() || snapshot.height() != next.height();

    snapshot = next;
    rebuildImage(next);
    // Reset the view only when loading a differently-sized world, not every tick
    if (dimensionsChanged) {
      viewInitialized = false;
      fillPanelWithWorld();
    }
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (cachedImage == null) {
      return;
    }
    Graphics2D g2 = (Graphics2D) g.create();

    try {
      // transform from world/tile coordinates to screen coordinates
      g2.translate(panX, panY);
      g2.scale(scale, scale);

      // keep terrain tiles pixelated
      g2.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

      g2.drawImage(cachedImage, 0, 0, null);
      drawBiomassDots(g2);
    } finally {
      g2.dispose();
    }
  }

  private void drawBiomassDots(Graphics2D g2) {
    byte[] biomass = snapshot.biomass();
    int worldWidth = snapshot.width();
    int worldHeight = snapshot.height();

    // Only inspect tiles currently visible on screen.
    int firstX = Math.max(0, (int) Math.floor(-panX / scale));
    int firstY = Math.max(0, (int) Math.floor(-panY / scale));

    int lastX = Math.min(worldWidth - 1, (int) Math.ceil((getWidth() - panX) / scale));
    int lastY = Math.min(worldHeight - 1, (int) Math.ceil((getHeight() - panY) / scale));

    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    // Each tile is 1x1 in world coordinates.
    double diameter = 0.72;
    double inset = (1.0 - diameter) / 2.0;

    for (int tileY = firstY; tileY <= lastY; tileY++) {
      int rowOffset = tileY * worldWidth;

      for (int tileX = firstX; tileX <= lastX; tileX++) {
        int quantity = Byte.toUnsignedInt(biomass[rowOffset + tileX]);

        if (quantity == 0) {
          continue;
        }

        int alpha = Math.min(255, 100 + quantity * 17);
        int argb = (alpha << 24) | (FOOD_COLOR_HEX & 0x00FF_FFFF);

        g2.setColor(new Color(argb, true));

        g2.fill(new Ellipse2D.Double(tileX + inset, tileY + inset, diameter, diameter));
      }
    }
  }

  void zoomAt(Point cursor, double wheelRotation) {
    if (cachedImage == null) {
      return;
    }

    if (!viewInitialized) {
      fillPanelWithWorld();
    }

    if (!viewInitialized) {
      return;
    }

    double oldScale = scale;
    double requestedScale = oldScale * Math.pow(ZOOM_FACTOR, -wheelRotation);

    // Zooming below coverScale would necessarily reveal black background.
    double newScale =
        Math.max(coverScale, Math.min(coverScale * MAX_RELATIVE_ZOOM, requestedScale));

    if (Double.compare(newScale, oldScale) == 0) {
      return;
    }

    double worldX = (cursor.x - panX) / oldScale;
    double worldY = (cursor.y - panY) / oldScale;

    scale = newScale;

    // Keep the same world position under the cursor.
    panX = cursor.x - worldX * newScale;
    panY = cursor.y - worldY * newScale;

    // Cursor anchoring can attempt to move an edge into the viewport.
    constrainPanToWorld();
    repaint();
  }

  void panBy(double dx, double dy) {
    if (!viewInitialized) {
      fillPanelWithWorld();
    }

    if (!viewInitialized) {
      return;
    }

    panX += dx;
    panY += dy;

    constrainPanToWorld();
    repaint();
  }

  public void resetView() {
    viewInitialized = false;
    fillPanelWithWorld();
  }

  private void fillPanelWithWorld() {
    if (cachedImage == null || getWidth() <= 0 || getHeight() <= 0) {
      return;
    }

    double scaleX = (double) getWidth() / cachedImage.getWidth();
    double scaleY = (double) getHeight() / cachedImage.getHeight();

    // removes black bars but might crop real-world tiles
    coverScale = Math.max(scaleX, scaleY);
    scale = coverScale;

    // Center the image in any unused space.
    panX = (getWidth() - cachedImage.getWidth() * scale) / 2.0;
    panY = (getHeight() - cachedImage.getHeight() * scale) / 2.0;

    constrainPanToWorld();
    viewInitialized = true;
    repaint();
  }

  private void constrainPanToWorld() {
    if (cachedImage == null) {
      return;
    }

    double worldWidth = cachedImage.getWidth() * scale;
    double worldHeight = cachedImage.getHeight() * scale;

    panX = constrainAxis(panX, getWidth(), worldWidth);
    panY = constrainAxis(panY, getHeight(), worldHeight);
  }

  private static double constrainAxis(double position, double viewportSize, double worldSize) {

    // Normally impossible because scale >= coverScale, but this also
    // protects against floating-point and initialization edge cases.
    if (worldSize <= viewportSize) {
      return (viewportSize - worldSize) / 2.0;
    }

    // The image origin must remain between:
    //
    // viewportSize - worldSize: far edge aligned
    // 0:                         near edge aligned
    double minimumPosition = viewportSize - worldSize;
    double maximumPosition = 0.0;

    return Math.max(minimumPosition, Math.min(maximumPosition, position));
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
      // color terrain
      pixelBuffer[i] = TILE_RGB[terrain[i]];
    }
    cachedImage.setRGB(0, 0, width, height, pixelBuffer, 0, width);
  }

  /// KEY Bindings for panning
  private void installPanKeyBindings() {
    // Camera semantics:
    // W moves the camera north, so the rendered world moves downward.
    bindPanKey(KeyEvent.VK_W, "camera-up", 0, KEYBOARD_PAN_STEP);

    bindPanKey(KeyEvent.VK_S, "camera-down", 0, -KEYBOARD_PAN_STEP);

    bindPanKey(KeyEvent.VK_A, "camera-left", KEYBOARD_PAN_STEP, 0);

    bindPanKey(KeyEvent.VK_D, "camera-right", -KEYBOARD_PAN_STEP, 0);
  }

  private void bindPanKey(int keyCode, String actionName, double dx, double dy) {

    getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(keyCode, 0), actionName);

    getActionMap()
        .put(
            actionName,
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent e) {
                // Prevent the hidden simulation card from reacting.
                if (isShowing()) {
                  panBy(dx, dy);
                }
              }
            });
  }
}
