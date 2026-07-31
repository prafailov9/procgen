package com.ntros;

import static com.ntros.Renderable.APP_WINDOW;
import static com.ntros.Renderable.WORLD_OBJECT;

public record Dimensions2d(int width, int height) {

  private static final int MAX_ALLOWED_WORLD_WIDTH = 1920;
  private static final int MAX_ALLOWED_WORLD_HEIGHT = 1080;

  private static final int MAX_ALLOWED_WINDOW_WIDTH = 2560;
  private static final int MAX_ALLOWED_WINDOW_HEIGHT = 1440;

  // TODO: fix. screen is only displaying smaller worlds in the top-left corner, rest is black.
  public static Dimensions2d ofSmallWorld() {
    return ofRenderable(WORLD_OBJECT, 490, 270);
  }

  public static Dimensions2d ofMediumWorld() {
    return ofRenderable(WORLD_OBJECT, 800, 600);
  }

  public static Dimensions2d ofBigWorld() {
    return ofRenderable(WORLD_OBJECT, 1920, 1080);
  }

  public static Dimensions2d ofBiggerWorld() {
    return ofRenderable(WORLD_OBJECT, 2560, 1440);
  }

  public static Dimensions2d ofMainAppWindow() {
    return ofRenderable(APP_WINDOW, 2560, 1440);
  }

  private static Dimensions2d ofRenderable(Renderable renderable, int width, int height) {
    if (renderable.equals(WORLD_OBJECT)) {
      return new Dimensions2d(
          Math.min(width, MAX_ALLOWED_WORLD_WIDTH), Math.min(height, MAX_ALLOWED_WORLD_HEIGHT));
    }

    if (renderable.equals(APP_WINDOW)) {
      return new Dimensions2d(
          Math.min(width, MAX_ALLOWED_WINDOW_WIDTH), Math.min(height, MAX_ALLOWED_WINDOW_HEIGHT));
    }
    throw new IllegalArgumentException("Invalid renderable: " + renderable);
  }
}
