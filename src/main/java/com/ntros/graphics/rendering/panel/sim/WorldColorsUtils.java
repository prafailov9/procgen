package com.ntros.graphics.rendering.panel.sim;

import com.ntros.core.world.terrain.Tile;
import com.ntros.core.ecs.store.CreatureType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

public class WorldColorsUtils {

  private static final Logger log = LoggerFactory.getLogger(WorldColorsUtils.class);

  public static final Color DEEP_WATER_COLOR = new Color(7, 13, 113);
  public static final Color SHALLOW_WATER_COLOR = new Color(56, 129, 255);
  public static final Color DRINKABLE_WATER_COLOR = new Color(72, 205, 210);
  public static final Color SAND_COLOR = new Color(237, 201, 175);

  public static final Color GRASS_COLOR = new Color(41, 202, 30);
  public static final Color FORREST_COLOR = new Color(13, 133, 29);
  public static final Color HILL_COLOR = new Color(164, 168, 119);
  public static final Color MOUNTAIN_COLOR = new Color(227, 227, 218);
  public static final Color EMPTY_COLOR = Color.BLACK;

  // TODO: Extend with different types
  public static final int FOOD_COLOR_HEX = 0x00E6FF59;

  public static final Color RABBIT_COLOR = new Color(248, 246, 240);
  public static final Color FOX_COLOR = new Color(255, 106, 0);
  // dark disc drawn under each creature so dots stay readable on any terrain
  public static final Color CREATURE_SHADOW_COLOR = new Color(20, 20, 20);

  // species bytes are CreatureType ordinals; index straight into this lookup when drawing
  public static final Color[] CREATURE_COLORS = buildCreatureColorLookup();

  // tiles bytes are Tile ordinals; index straight into this lookup when building the image
  public static final int[] TILE_RGB = buildTileRgbLookup();

  private static Color[] buildCreatureColorLookup() {
    CreatureType[] types = CreatureType.values();
    Color[] colors = new Color[types.length];
    for (int i = 0; i < types.length; i++) {
      colors[i] =
          switch (types[i]) {
            case RABBIT -> RABBIT_COLOR;
            case FOX -> FOX_COLOR;
          };
    }
    return colors;
  }

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
      case FRESH_WATER -> DRINKABLE_WATER_COLOR;
      case SAND -> SAND_COLOR;
      case GRASS -> GRASS_COLOR;
      case FOREST -> FORREST_COLOR;
      case HILL -> HILL_COLOR;
      case MOUNTAIN -> MOUNTAIN_COLOR;
      default -> EMPTY_COLOR;
    };
  }
}
