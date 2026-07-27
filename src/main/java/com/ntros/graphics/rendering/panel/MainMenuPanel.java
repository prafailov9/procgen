package com.ntros.graphics.rendering.panel;

import static com.ntros.graphics.ScreenType.WORLD_SETUP;

import com.ntros.MainSettings;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class MainMenuPanel extends AbstractScreenPanel {
  // Options: New game, Load game, Settings, Exit

  private BufferedImage cachedMenuImage;

  public MainMenuPanel(ScreenController screenController) {
      super(screenController);
    setPreferredSize(new Dimension(MainSettings.WIDTH, MainSettings.HEIGHT));
    setLayout(new GridBagLayout());
    setOpaque(true);

    add(createMenuPanel());
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (cachedMenuImage == null) {
      return;
    }

    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

    g2.drawImage(cachedMenuImage, 0, 0, getWidth(), getHeight(), null);
    g2.dispose();
  }

  private JPanel createMenuPanel() {
    JPanel menu = new JPanel();

    menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
    menu.setBorder(new EmptyBorder(25, 50, 25, 50));

    // Keep the painted background visible.
    menu.setOpaque(false);

    JLabel title = new JLabel("ProcGen");
    title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 45));
    title.setForeground(Color.WHITE);
    title.setAlignmentX(Component.CENTER_ALIGNMENT);

    JButton newGameButton = createButton("New Game");
    JButton loadGameButton = createButton("Load Game");
    JButton settingsButton = createButton("Settings");
    JButton exitButton = createButton("Exit");

    newGameButton.addActionListener(event -> screenController.show(WORLD_SETUP));

    // TODO: implement save/loading
    //        loadGameButton.addActionListener(event -> {
    //            screenController.showLoadGameScreen();
    //        });

    // TODO: implement settings
    //        settingsButton.addActionListener(event -> {
    //            screenController.showSettingsScreen();
    //        });

    exitButton.addActionListener(
        event -> {
          int choice =
              JOptionPane.showConfirmDialog(
                  this, "Are you sure you want to exit?", "Exit Game", JOptionPane.YES_NO_OPTION);

          if (choice == JOptionPane.YES_OPTION) {
            System.exit(0);
          }
        });

    menu.add(title);
    menu.add(Box.createVerticalStrut(40));
    menu.add(newGameButton);
    menu.add(Box.createVerticalStrut(12));
    menu.add(loadGameButton);
    menu.add(Box.createVerticalStrut(12));
    menu.add(settingsButton);
    menu.add(Box.createVerticalStrut(12));
    menu.add(exitButton);

    return menu;
  }

  private JButton createButton(String text) {
    JButton button = new JButton(text);
    // tune values if needed
    button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
    button.setAlignmentX(Component.CENTER_ALIGNMENT);
    button.setMaximumSize(new Dimension(240, 50));
    button.setPreferredSize(new Dimension(240, 50));
    button.setFocusable(false);

    return button;
  }

  public void setMenuImage(BufferedImage image) {
    cachedMenuImage = image;
    repaint();
  }
}
