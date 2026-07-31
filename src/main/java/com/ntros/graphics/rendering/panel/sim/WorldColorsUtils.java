package com.ntros.graphics.rendering.panel.sim;

import com.ntros.core.world.terrain.Tile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

public class WorldColorsUtils {

  private static final Logger log = LoggerFactory.getLogger(WorldColorsUtils.class);

  public static final Color DEEP_WATER_COLOR = new Color(7, 13, 113);
  public static final Color SHALLOW_WATER_COLOR = new Color(56, 129, 255);
  public static final Color SAND_COLOR = new Color(237, 201, 175);

  public static final Color GRASS_COLOR = new Color(41, 202, 30);
  public static final Color FORREST_COLOR = new Color(13, 133, 29);
  public static final Color HILL_COLOR = new Color(164, 168, 119);
  public static final Color MOUNTAIN_COLOR = new Color(227, 227, 218);
  public static final Color EMPTY_COLOR = Color.BLACK;

  // tiles bytes are Tile ordinals; index straight into this lookup when building the image
  public static final int[] TILE_RGB = buildTileRgbLookup();

  public static int[] buildTileRgbLookup() {
    Tile[] tiles = Tile.values();
    int[] rgb = new int[tiles.length];
    for (int i = 0; i < tiles.length; i++) {
      rgb[i] = getTileColor(tiles[i]).getRGB();
    }
    return rgb;
  }

  public static Color getTileColor(Tile tile) {
    return switch (tile) {
      case DEEP_WATER -> DEEP_WATER_COLOR;
      case SHALLOW_WATER -> SHALLOW_WATER_COLOR;
      case SAND -> SAND_COLOR;
      case GRASS -> GRASS_COLOR;
      case FOREST -> FORREST_COLOR;
      case HILL -> HILL_COLOR;
      case MOUNTAIN -> MOUNTAIN_COLOR;
      default -> EMPTY_COLOR;
    };
  }
}
