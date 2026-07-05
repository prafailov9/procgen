package com.ntros;

import com.ntros.generator.WorldGenerator;
import com.ntros.generator.rendering.WorldPanel;
import java.awt.BorderLayout;
import java.net.URL;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

public class Main {

  private static final String ICON_FILE = "/assets/icon.png";

  public static void main(String[] args) {

    // 1. input
    // 2. simulation
    // 3. render

    SwingUtilities.invokeLater(
        () -> {
          WorldGenerator generator = new WorldGenerator();
          generator.initialize(256, 256);

          JLabel label = new JLabel();
          WorldPanel worldPanel = new WorldPanel(generator.getWorld());

          JFrame frame = new JFrame("World Gen");
          frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
          frame.setLayout(new BorderLayout());

          frame.add(label, BorderLayout.NORTH);
          frame.add(worldPanel, BorderLayout.CENTER);

          frame.setIconImage(new ImageIcon(getIconURL()).getImage());
          frame.pack();
          frame.setLocationRelativeTo(null);
          frame.setVisible(true);

          SwingWorker<Void, Void> worker =
              new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                  int maxIterations = 1000;

                  for (int i = 0; i < maxIterations; i++) {
                    generator.step();

                    SwingUtilities.invokeLater(
                        () -> {
                          worldPanel.setWorld(generator.getWorld());
                          label.setText(
                              String.format(
                                  "Iterations: %d, Conflicts: %d",
                                  generator.getIterations(), generator.getConflicts()));
                        });

                    if (generator.getConflicts() == 0) {
                      break;
                    }

                    Thread.sleep(29);
                  }

                  return null;
                }
              };

          worker.execute();
        });

    int rows = 12;
    int cols = 14;
    int[] r = WorldGenerator.generateRef(rows, cols);

    for (int y = 0; y < rows; y++) {
      for (int x = 0; x < cols; x++) {
        System.out.printf("|%s| ", r[y * cols + x]);
      }
      System.out.println();
    }
  }

  private static URL getIconURL() {
    URL iconUrl = Main.class.getResource(ICON_FILE);
    if (iconUrl == null) {
      throw new RuntimeException("Icon not found: " + ICON_FILE);
    }
    return iconUrl;
  }
}
