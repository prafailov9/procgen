package com.ntros.graphics.rendering.panel;

import javax.swing.*;

public abstract class AbstractScreenPanel extends JPanel {

    protected final ScreenController screenController;

    protected AbstractScreenPanel(ScreenController screenController) {
        this.screenController = screenController;
    }

}
