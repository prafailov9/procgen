package com.ntros;

import com.ntros.graphics.rendering.data.Dimensions2d;
import com.ntros.graphics.rendering.data.WorldSize;

import java.util.Map;

import static com.ntros.graphics.rendering.data.Dimensions2d.*;
import static com.ntros.graphics.rendering.data.WorldSize.*;

public class AppConstants {
  public static final int MAIN_WINDOW_WIDTH = 2560;
  public static final int MAIN_WINDOW_HEIGHT = 1440;
  public static final String TILE_ENCODINGS_FILEPATH = "/tile-encodings.properties";
  public static final int CREATURES_CAPACITY = 5_000; // low for now
  public static final float CLUSTER_BIOMASS_CHANCE = 0.029f; // decides if spawning in clusters
  public static final float BIOMASS_SPAWN_CHANCE = 0.012f; // decides whether food spawns at all
  public static final float PREDATOR_SPAWN_CHANCE = 0.10f;
  public static final float CREATURE_MAX_ENERGY = 100.00f;
  public static final int CREATURE_START_AGE = 1;

  public static Map<WorldSize, Dimensions2d> WORLD_SIZES_ALLOWLIST =
      Map.of(
          TINY,
          ofTinyWorld(),
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
