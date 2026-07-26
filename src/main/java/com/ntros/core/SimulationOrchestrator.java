package com.ntros.core;

import com.ntros.generator.WorldGenerator;
import com.ntros.generator.rendering.WorldPanel;

import javax.swing.*;

public class SimulationOrchestrator {
    private final Timer timer;
    private final WorldPanel worldPanel;

    public SimulationOrchestrator(int delayMs, WorldGenerator worldGenerator, WorldPanel worldPanel) {
        if (delayMs <= 0) {
            throw new IllegalArgumentException("DelayMS must be positive");
        }
        this.worldPanel = worldPanel;
        timer = new Timer(delayMs, event -> {
            worldGenerator.step();
//            worldPanel.redraw(worldGenerator.getGenStats());
            worldPanel.repaint();
        });
    }

    public void start() {
        timer.start();
    }

    public void stop() {
        timer.stop();
    }
}
