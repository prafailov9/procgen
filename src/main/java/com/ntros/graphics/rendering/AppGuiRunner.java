package com.ntros.graphics.rendering;

import com.ntros.core.world.WorldGenerationSettings;
import com.ntros.graphics.rendering.screens.MainMenuPanel;
import com.ntros.graphics.rendering.screens.ScreenController;
import com.ntros.graphics.rendering.screens.WorldSetupPanel;
import com.ntros.graphics.rendering.screens.WorldSimPanel;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

import static com.ntros.graphics.ScreenType.*;

/**
 * Handles the Main App window.
 */
public final class AppGuiRunner {
    private final int windowWidth;
    private final int windowHeight;
    private final JPanel screens;
    private final ScreenController screenController;
    private final WorldSetupPanel worldSetupPanel;
    private final WorldSimPanel worldSimPanel;

    public AppGuiRunner(int windowWidth, int windowHeight, Consumer<WorldGenerationSettings> genCallback) {
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;

        // create a card layout for switching between screens
        CardLayout cardLayout = new CardLayout();
        // screens panel to maintain all the screens
        screens = new JPanel(cardLayout);

        // screen controller holds the panel and displays based on screen id
        screenController = new ScreenController(screens, cardLayout);
        MainMenuPanel mainMenuPanel = new MainMenuPanel(screenController);
        worldSetupPanel = new WorldSetupPanel(screenController, genCallback);
        worldSimPanel = new WorldSimPanel(screenController);

        // add panels to the screen controller panel
        screens.add(mainMenuPanel, MAIN_MENU.name());
        screens.add(worldSetupPanel, WORLD_SETUP.name());
        screens.add(worldSimPanel, SIMULATION.name());
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

  public void startGuiApp() {
    SwingUtilities.invokeLater(
        () -> {
            // create the main window
            JFrame frame = new JFrame("ProcGen");
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

            frame.setContentPane(screens);
            frame.setSize(windowWidth, windowHeight);
            // center
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
  }
}
