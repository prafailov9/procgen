package com.ntros.graphics;

import java.util.Properties;

public final class WindowConfig {

  private static final String PROPERTIES_FILENAME = "window.properties";

  private static final String WIDTH_KEY = "window.width";
  private static final String HEIGHT_KEY = "window.height";
  private static final String TITLE_KEY = "window.title";

  private static final int DEFAULT_WIDTH = 1920;
  private static final int DEFAULT_HEIGHT = 1080;
  private static final String DEFAULT_TITLE = "title";

  private final int width;
  private final int height;
  private final String title;

  private WindowConfig(int width, int height, String title) {
    this.width = width;
    this.height = height;
    this.title = title;
  }

  public static WindowConfig load() {
    System.out.println("Loading properties...");
    Properties properties = SettingsLoader.load(PROPERTIES_FILENAME);

    return new WindowConfig(
        parseToInt(properties, WIDTH_KEY, DEFAULT_WIDTH, "width"),
        parseToInt(properties, HEIGHT_KEY, DEFAULT_HEIGHT, "height"),
        properties.getProperty(TITLE_KEY, DEFAULT_TITLE));
  }

  private static int parseToInt(Properties properties, String key, int defaultValue, String label) {
    try {
      return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Invalid " + label + " value in properties file", ex);
    }
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public String getTitle() {
    return title;
  }
}
