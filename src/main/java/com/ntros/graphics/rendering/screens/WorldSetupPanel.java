package com.ntros.graphics.rendering.screens;

import com.ntros.core.world.WorldGenerationSettings;
import com.ntros.generator.fastnoiselite.NoiseSettings;
import com.ntros.graphics.ScreenType;

import javax.swing.*;
import java.awt.*;
import java.util.Random;
import java.util.function.Consumer;

public class WorldSetupPanel extends JPanel {

  // world dimensions in tiles, independent of the window size
  private static final int WORLD_WIDTH = 1920;
  private static final int WORLD_HEIGHT = 1080;

  private final ScreenController screenController;
  private final Consumer<WorldGenerationSettings> generationHandler;
  private final JTextField seedField = new JTextField(20);
  private final JButton generateButton = new JButton("Generate World");
  private final JLabel statusLabel = new JLabel(" ");

  public WorldSetupPanel(
      ScreenController screenController, Consumer<WorldGenerationSettings> generationHandler) {
    this.screenController = screenController;

    this.generationHandler = generationHandler;

    buildUi();
  }

  private void buildUi() {
    setLayout(new GridBagLayout());

    JPanel form = new JPanel();
    form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

    JLabel title = new JLabel("World Setup");
    title.setFont(title.getFont().deriveFont(Font.BOLD, 32f));
    title.setAlignmentX(Component.CENTER_ALIGNMENT);

    seedField.setText(Long.toString(new Random().nextLong()));
    seedField.setMaximumSize(new Dimension(300, 32));

    JButton randomSeedButton = new JButton("Random Seed");
    randomSeedButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    randomSeedButton.addActionListener(
        event -> seedField.setText(Long.toString(new Random().nextLong())));

    generateButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    generateButton.addActionListener(event -> requestGeneration());

    JButton backButton = new JButton("Back");
    backButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    backButton.addActionListener(event -> screenController.show(ScreenType.MAIN_MENU));

    statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

    form.add(title);
    form.add(Box.createVerticalStrut(30));
    form.add(new JLabel("Seed:"));
    form.add(seedField);
    form.add(Box.createVerticalStrut(8));
    form.add(randomSeedButton);
    form.add(Box.createVerticalStrut(20));
    form.add(generateButton);
    form.add(Box.createVerticalStrut(8));
    form.add(backButton);
    form.add(Box.createVerticalStrut(15));
    form.add(statusLabel);

    add(form);
  }

  private void requestGeneration() {
    final long seed;

    try {
      seed = Long.parseLong(seedField.getText().trim());
    } catch (NumberFormatException exception) {
      statusLabel.setText("Seed must be a whole number.");
      return;
    }

    // TODO: let the player tune the Noise settings
    WorldGenerationSettings settings =
        new WorldGenerationSettings(
            WORLD_WIDTH, WORLD_HEIGHT, seed, new NoiseSettings(0.0025f, 5, 0.0032f, 3, 0.005f, 5));

    // TODO: add proceed or regenerate buttons
    // generation runs off the EDT; the handler switches to the sim screen when it finishes
    setGenerating(true);
    generationHandler.accept(settings);
  }

  public void setGenerating(boolean generating) {
    generateButton.setEnabled(!generating);
    seedField.setEnabled(!generating);
    statusLabel.setText(generating ? "Generating world..." : " ");
  }

  public void showGenerationError(Throwable error) {
    setGenerating(false);
    statusLabel.setText("World generation failed.");

    JOptionPane.showMessageDialog(
        this, error.getMessage(), "Generation Error", JOptionPane.ERROR_MESSAGE);
  }
}
