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

  public static Dimensions2d getSizeByOrdinal(int ordinal) {
    if (ordinal < 0 || ordinal > WORLD_SIZES_ALLOWLIST.size() - 1) {
      throw new IllegalArgumentException("Invalid WorldSize ordinal requested: " + ordinal);
    }
    return WORLD_SIZES_ALLOWLIST.get(WorldSize.values()[ordinal]);
  }
}
