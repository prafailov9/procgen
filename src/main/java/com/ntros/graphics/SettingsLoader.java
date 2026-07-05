package com.ntros.graphics;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Properties;

public final class SettingsLoader {

  private SettingsLoader() {}

  public static Properties load(String propertiesFilename) {
    InputStream stream = SettingsLoader.class.getResourceAsStream(propertiesFilename);
    if (stream == null) {
      throw new IllegalArgumentException("Properties file not found: " + propertiesFilename);
    }

    Properties properties = new Properties();
    try (Reader reader = new InputStreamReader(stream)) {
      properties.load(reader);
      return properties;
    } catch (IOException ex) {
      throw new RuntimeException(
          String.format("Could not read properties file: %s", propertiesFilename), ex);
    }
  }
}
