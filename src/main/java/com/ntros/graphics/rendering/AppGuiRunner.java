package com.ntros.graphics.rendering;

import static com.ntros.graphics.ScreenType.*;

import com.ntros.core.control.IntentTranslator;
import com.ntros.core.world.terrain.TerrainGenerationSettings;
import com.ntros.graphics.rendering.panel.*;
import com.ntros.graphics.rendering.panel.sim.WorldSimPanel;
import java.awt.*;
import java.util.function.Consumer;
import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the Main App window: builds the frame, the screens and their controller. All Swing
 * components live here, so construction must happen on the EDT. enforced in the constructor.
 */
public final class AppGuiRunner {
  private static final Logger log = LoggerFactory.getLogger(AppGuiRunner.class);

  private final JFrame frame;
  private final ScreenController screenController;
  private final WorldSetupPanel worldSetupPanel;
  private final WorldSimPanel worldSimPanel;
  private final PauseMenuPanel pauseMenuPanel;

  /** Setup of the Main Window, its screens and controller. Must be called on the EDT. */
  public AppGuiRunner(
      int windowWidth,
      int windowHeight,
      long seed,
      Consumer<TerrainGenerationSettings>
          genCallback, // callback function to trigger world generation
      IntentTranslator intentTranslator) {
    if (!SwingUtilities.isEventDispatchThread()) {
      throw new IllegalStateException("AppGuiRunner must be created on the EDT");
    }

    log.info("Setting up screens...");

    // TODO: add own assets

    // create a card layout for switching between screens
    CardLayout cardLayout = new CardLayout();
    // screens panel to maintain all the screens
    JPanel screens = new JPanel(cardLayout);

    // screen controller holds the panel and displays based on screen id
    screenController = new ScreenController(screens, cardLayout);
    MainMenuPanel mainMenuPanel = new MainMenuPanel(screenController);
    worldSetupPanel = new WorldSetupPanel(screenController, genCallback, seed);
    worldSimPanel = new WorldSimPanel(screenController, intentTranslator);
    pauseMenuPanel = new PauseMenuPanel(screenController);

    // add panels to the screen controller panel
    screens.add(mainMenuPanel, MAIN_MENU.name());
    screens.add(worldSetupPanel, WORLD_SETUP.name());
    screens.add(worldSimPanel, SIMULATION.name());
    screens.add(pauseMenuPanel, PAUSE_MENU.name());

    // the main window
    frame = new JFrame("ProcGen");
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    frame.setContentPane(screens);
    frame.setSize(windowWidth, windowHeight);
    // center
    frame.setLocationRelativeTo(null);
  }

  public ScreenController getScreenController() {
    return screenController;
  }

  public WorldSetupPanel getWorldSetupPanel() {
    return worldSetupPanel;
  }

  public WorldSimPanel getWorldSimPanel() {
    return worldSimPanel;
  }

  public PauseMenuPanel getPauseMenuPanel() {
    return pauseMenuPanel;
  }

  /** Makes the already-built main window visible. Must be called on the EDT. */
  public void showMainWindow() {
    frame.setVisible(true);
  }
}
