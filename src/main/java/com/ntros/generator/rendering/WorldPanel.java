package com.ntros.generator.rendering;

import com.ntros.generator.Tile;
import com.ntros.generator.world.World;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

public class WorldPanel extends JPanel implements MouseWheelListener {

  private static final int ZOOM_STEP = 1;
  private static final int MIN_TILE_SIZE = 1;
  private static final int MAX_TILE_SIZE = 128;

  // cached tile colors
  private static final Color DEEP_WATER_COLOR = new Color(7, 13, 113);
  private static final Color SHALLOW_WATER_COLOR = new Color(56, 129, 255);
  private static final Color GRASS_COLOR = new Color(41, 202, 30);
  private static final Color FORREST_COLOR = new Color(13, 133, 29);
  private static final Color HILL_COLOR = new Color(164, 168, 119);
  private static final Color MOUNTAIN_COLOR = new Color(227, 227, 218);
  private static final Color EMPTY_COLOR = Color.BLACK;

  private World world;
  private int tileSize = 128;
  // prebuilt rendering image of the world
  private BufferedImage cachedImage;

  public WorldPanel(World world) {
    this.world = world;
    addMouseWheelListener(this);
    setFocusable(true);
    setBackground(Color.BLACK);
    // prebuilds the image based off initial generation of the world
    rebuildImage();
  }

  // on each call - update world and image
  public void setWorld(World world) {
    this.world = world;
    rebuildImage();
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (world == null || cachedImage == null) {
      return;
    }

    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

    int panelWidth = getWidth();
    int panelHeight = getHeight();

    g2.drawImage(cachedImage, 0, 0, panelWidth, panelHeight, null);
    g2.dispose();
  }

  private void rebuildImage() {
    if (world == null || world.getWidth() <= 0 || world.getHeight() <= 0) {
      cachedImage = null;
      return;
    }

    cachedImage =
        new BufferedImage(world.getWidth(), world.getHeight(), BufferedImage.TYPE_INT_RGB);

    for (int y = 0; y < world.getHeight(); y++) {
      for (int x = 0; x < world.getWidth(); x++) {
        cachedImage.setRGB(x, y, getTileColor(world.getTile(x, y)).getRGB());
      }
    }
  }

  private Color getTileColor(Tile tile) {
    return switch (tile) {
      case DEEP_WATER -> DEEP_WATER_COLOR;
      case SHALLOW_WATER -> SHALLOW_WATER_COLOR;
      case GRASS -> GRASS_COLOR;
      case FORREST -> FORREST_COLOR;
      case HILL -> HILL_COLOR;
      case MOUNTAIN -> MOUNTAIN_COLOR;
      default -> EMPTY_COLOR;
    };
  }

  @Override
  public void mouseWheelMoved(MouseWheelEvent e) {
    int rotation = e.getWheelRotation();

    if (rotation < 0) {
      zoomIn();
    } else if (rotation > 0) {
      zoomOut();
    }
  }

  public void zoomIn() {
    tileSize = Math.min(MAX_TILE_SIZE, tileSize + ZOOM_STEP);
    rebuildImage();
    repaint();
  }

  public void zoomOut() {
    tileSize = Math.max(MIN_TILE_SIZE, tileSize - ZOOM_STEP);
    rebuildImage();
    repaint();
  }

  public int getTileSize() {
    return tileSize;
  }

  public void setTileSize(int tileSize) {
    this.tileSize = Math.max(MIN_TILE_SIZE, Math.min(MAX_TILE_SIZE, tileSize));
    rebuildImage();
    repaint();
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(800, 800);
  }
}
