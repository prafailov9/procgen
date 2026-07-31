package com.ntros.graphics.rendering.panel.sim;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Objects;
import javax.swing.SwingUtilities;

public final class WorldSimMouseHandler extends MouseAdapter {

  private final WorldSimPanel panel;
  private Point lastDragPoint;

  public WorldSimMouseHandler(WorldSimPanel panel) {
    this.panel = Objects.requireNonNull(panel);
  }

  @Override
  public void mousePressed(MouseEvent e) {
    if (SwingUtilities.isMiddleMouseButton(e)) {
      lastDragPoint = e.getPoint();
      panel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
      e.consume();
    }
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    if (SwingUtilities.isMiddleMouseButton(e)) {
      lastDragPoint = null;
      panel.setCursor(Cursor.getDefaultCursor());
      e.consume();
    }
  }

  @Override
  public void mouseWheelMoved(MouseWheelEvent e) {
    panel.zoomAt(e.getPoint(), e.getPreciseWheelRotation());
    e.consume();
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    if (lastDragPoint == null) {
      return;
    }

    panel.panBy(e.getX() - lastDragPoint.x, e.getY() - lastDragPoint.y);

    lastDragPoint = e.getPoint();
    e.consume();
  }
}
