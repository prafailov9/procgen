package com.ntros.keyboard;

import static java.awt.event.KeyEvent.*;

import com.ntros.core.SimulationSpeed;
import com.ntros.core.control.IntentTranslator;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class WorldSimKeyListener extends KeyAdapter {

  private final IntentTranslator controls;

  public WorldSimKeyListener(IntentTranslator controls) {
    this.controls = controls;
  }

  @Override
  public void keyPressed(KeyEvent e) {
    switch (e.getKeyCode()) {
      case VK_1 -> controls.changeSpeed(SimulationSpeed.X1);
      case VK_2 -> controls.changeSpeed(SimulationSpeed.X5);
      case VK_3 -> controls.changeSpeed(SimulationSpeed.X25);
      case VK_4 -> controls.changeSpeed(SimulationSpeed.X250);
      case VK_5 -> controls.changeSpeed(SimulationSpeed.MAX);
      case VK_0 -> controls.changeSpeed(SimulationSpeed.PAUSED);
      default -> {}
    }
  }
}
