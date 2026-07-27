package com.ntros.graphics.rendering.screens;

import com.ntros.graphics.ScreenType;

import javax.swing.*;
import java.awt.*;

public class ScreenController {

  private final JPanel screensContainer;
  private final CardLayout cardLayout;

  public ScreenController(JPanel jPanel, CardLayout cardLayout) {
    this.screensContainer = jPanel;
    this.cardLayout = cardLayout;
  }

  public void show(ScreenType screen) {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(() -> show(screen));
      return;
    }

    cardLayout.show(screensContainer, screen.name());
  }
}
