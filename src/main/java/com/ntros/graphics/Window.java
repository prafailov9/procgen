package com.ntros.graphics;

public class Window {

  private final String title;
  private final int width;
  private final int height;

  private Window() {
    WindowConfig config = WindowConfig.load();
    width = config.getWidth();
    height = config.getHeight();
    title = config.getTitle();
    System.out.printf("Loaded properties. width: %s, height: %s, title: %s", width, height, title);
  }

  public static Window getWindow() {
    return WindowInstanceHolder.INSTANCE;
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

  private static final class WindowInstanceHolder {
    private static final Window INSTANCE = new Window();
  }
}
