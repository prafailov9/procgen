package com.ntros.graphics.rendering.panel.sim;

import static com.ntros.graphics.rendering.panel.sim.WorldSimConstants.MIN_TILE_SIZE;
import static com.ntros.graphics.rendering.panel.sim.WorldSimConstants.ZOOM_STEP;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.*;

public class WorldSimMouseHandler {

  private int tileSize;

  private double panX = 0;
  private double panY = 0;

  private Point lastDragPoint;

  private final MouseAdapter mouseAdapter;

  public WorldSimMouseHandler(int tileSize, WorldSimPanel worldSimPanel) {
    if (tileSize < 0) {
      throw new IllegalArgumentException("TileSize must be positive");
    }

    this.tileSize = tileSize;
    mouseAdapter = configureMouseHandler(worldSimPanel);
  }

  public MouseAdapter getMouseAdapter() {
    return mouseAdapter;
  }

  private MouseAdapter configureMouseHandler(WorldSimPanel worldSimPanel) {
    return new MouseAdapter() {

      @Override
      public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isMiddleMouseButton(e)) {
          lastDragPoint = e.getPoint();
          worldSimPanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isMiddleMouseButton(e)) {
          lastDragPoint = null;
          worldSimPanel.setCursor(Cursor.getDefaultCursor());
        }
      }

      @Override
      public void mouseWheelMoved(MouseWheelEvent e) {
        int oldTileSize = tileSize;
        int next = oldTileSize - e.getWheelRotation() * ZOOM_STEP;
        int newTileSize = Math.max(MIN_TILE_SIZE, next);

        if (newTileSize == oldTileSize) {
          return;
        }
        // determine which x,y is under the cursor
        // before zooming
        double worldX = (e.getX() - panX) / oldTileSize;
        double worldY = (e.getY() - panY) / oldTileSize;

        tileSize = newTileSize;
        // move the origin point so the same position under the cursor after zooming
        panX = e.getX() - worldX * newTileSize;
        panY = e.getY() - worldY * newTileSize;
        worldSimPanel.revalidate();
        worldSimPanel.repaint();
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        if (lastDragPoint == null) {
          return;
        }

        panX += e.getX() - lastDragPoint.x;
        panY += e.getY() - lastDragPoint.y;

        lastDragPoint = e.getPoint();
        worldSimPanel.repaint();
      }
    };
  }
}
