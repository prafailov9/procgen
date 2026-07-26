package com.ntros;

import com.ntros.core.SimulationOrchestrator;
import com.ntros.generator.GenStats;
import com.ntros.generator.WorldGenerator;
import com.ntros.generator.rendering.WorldPanel;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

public class Main {

    private static final String ICON_FILE = "/assets/icon.png";

    private static JFrame createWindow(WorldPanel worldPanel, JLabel label) {
        JFrame frame = new JFrame("World Gen");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        frame.add(label, BorderLayout.NORTH);
        frame.add(worldPanel, BorderLayout.CENTER);


        return frame;
    }

    public static void main(String[] args) {

        // 1. input
        // 2. simulation
        // 3. render
        GenStats genStats = new GenStats(0, 0);
        WorldGenerator generator = new WorldGenerator(256, 256, genStats);
        WorldPanel worldPanel = new WorldPanel(generator.getWorld());

        SwingUtilities.invokeLater(
                () -> {
                    JLabel label = new JLabel();
                    JFrame frame = createWindow(worldPanel, label);

                    frame.setIconImage(new ImageIcon(getIconURL()).getImage());
                    frame.pack();
                    frame.setLocationRelativeTo(null);
                    frame.setVisible(true);
                });


        SimulationOrchestrator orchestrator = new SimulationOrchestrator(25, generator, worldPanel);
        Runtime.getRuntime().addShutdownHook(new Thread(orchestrator::stop));
        orchestrator.start();
    }

    private static URL getIconURL() {
        URL iconUrl = Main.class.getResource(ICON_FILE);
        if (iconUrl == null) {
            throw new RuntimeException("Icon not found: " + ICON_FILE);
        }
        return iconUrl;
    }
}
