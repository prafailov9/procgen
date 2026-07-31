package com.ntros;

import com.ntros.core.world.terrain.Tile;
import com.ntros.graphics.rendering.data.Dimensions2d;
import com.ntros.graphics.rendering.data.WorldSize;

import java.util.List;
import java.util.Map;

import static com.ntros.graphics.rendering.data.Dimensions2d.*;
import static com.ntros.graphics.rendering.data.WorldSize.*;

public class AppConstants {
  public static final int MAIN_WINDOW_WIDTH = 2560;
  public static final int MAIN_WINDOW_HEIGHT = 1440;
  public static final String TILE_ENCODINGS_FILEPATH = "/tile-encodings.properties";
  public static final int CREATURES_CAPACITY = 500; // low for now

  public static Map<WorldSize, Dimensions2d> WORLD_SIZES_ALLOWLIST =
      Map.of(
          SMALL,
          ofSmallWorld(),
          MEDIUM,
          ofMediumWorld(),
          BIG,
          ofBigWorld(),
          BIGGER,
          ofBiggerWorld(),
          MASSIVE,
          ofMassiveWorld());
}
