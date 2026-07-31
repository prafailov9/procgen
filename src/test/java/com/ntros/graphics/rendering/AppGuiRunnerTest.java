package com.ntros.graphics.rendering;

import com.ntros.core.control.SwappableIntentTranslator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;

class AppGuiRunnerTest {

  private AppGuiRunner runner;

  @BeforeEach
  public void setup() {
    SwingUtilities.invokeLater(
        () ->
            runner =
                new AppGuiRunner(
                    1280,
                    720,
                    1,
                    _ -> System.out.println("Hello"),
                    new SwappableIntentTranslator()));
  }

  @Test
  public void testScreensTraversal() {}
}
