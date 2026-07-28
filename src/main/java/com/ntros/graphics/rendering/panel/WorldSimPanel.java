package com.ntros.graphics.rendering.panel;

import com.ntros.core.world.Tile;
import com.ntros.core.world.WorldSnapshot;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import javax.swing.*;

/** ESC - pause sim, display PAUSE_MENU [1 - 5] - Speed change commands. */
public class WorldSimPanel extends AbstractScreenPanel implements MouseWheelListener {

  private static final int ZOOM_STEP = 1;
  private static final int MIN_TILE_SIZE = 1;
  private static final int MAX_TILE_SIZE = 128;

  // cached tile colors
  private static final Color DEEP_WATER_COLOR = new Color(7, 13, 113);
  private static final Color SHALLOW_WATER_COLOR = new Color(56, 129, 255);
  private static final Color SAND_COLOR = new Color(237, 201, 175);

  private static final Color GRASS_COLOR = new Color(41, 202, 30);
  private static final Color FORREST_COLOR = new Color(13, 133, 29);
  private static final Color HILL_COLOR = new Color(164, 168, 119);
  private static final Color MOUNTAIN_COLOR = new Color(227, 227, 218);
  private static final Color EMPTY_COLOR = Color.BLACK;

  // terrain bytes are Tile ordinals; index straight into this lookup when building the image
  private static final int[] TILE_RGB = buildTileRgbLookup();

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
    addMouseWheelListener(this);
    setFocusable(true);
    setBackground(Color.BLACK);
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

  private static int[] buildTileRgbLookup() {
    Tile[] tiles = Tile.values();
    int[] rgb = new int[tiles.length];
    for (int i = 0; i < tiles.length; i++) {
      rgb[i] = getTileColor(tiles[i]).getRGB();
    }
    return rgb;
  }

  private static Color getTileColor(Tile tile) {
    return switch (tile) {
      case DEEP_WATER -> DEEP_WATER_COLOR;
      case SHALLOW_WATER -> SHALLOW_WATER_COLOR;
      case SAND -> SAND_COLOR;
      case GRASS -> GRASS_COLOR;
      case FORREST -> FORREST_COLOR;
      case HILL -> HILL_COLOR;
      case MOUNTAIN -> MOUNTAIN_COLOR;
      default -> EMPTY_COLOR;
    };
  }

  // TODO: zoom is anchored at the top-left corner; add panning + cursor-centered zoom
  @Override
  public void mouseWheelMoved(MouseWheelEvent e) {
    // wheel up (negative rotation) zooms in
    int next = tileSize - e.getWheelRotation() * ZOOM_STEP;
    int clamped = Math.max(MIN_TILE_SIZE, Math.min(MAX_TILE_SIZE, next));
    if (clamped != tileSize) {
      tileSize = clamped;
      revalidate();
      repaint();
    }
  }

  @Override
  public Dimension getPreferredSize() {
    if (snapshot == null) {
      return super.getPreferredSize();
    }
    return new Dimension(snapshot.width() * tileSize, snapshot.height() * tileSize);
  }
}
