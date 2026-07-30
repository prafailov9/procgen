package com.ntros.core.world;

import com.ntros.core.world.terrain.TerrainCodec;
import com.ntros.core.world.terrain.Tile;
import com.ntros.core.world.terrain.WorldTerrainSettings;

import static com.ntros.core.world.terrain.Tile.EMPTY;

/**
 * Representation of the 2D Simulation World. Updated by the StateProcessor, displayed by the
 * StateRenderer. Houses all visible and/or interactable game objects.
 */
public class World {

  private final int width, height;
  private final int size;
  // flat array representing a 2d grid, for faster cache access
  private final byte[] terrain;
  // TODO: add other game objects Struct-of-Arrays style.
  private final TerrainCodec terrainCodec;

  private World(int width, int height) {
    this.width = width;
    this.height = height;
    size = width * height;
    terrain = new byte[size];

    terrainCodec = new TerrainCodec();
    for (int x = 0; x < width; x++) {
      for (int y = 0; y < height; y++) {
        terrain[y * width + x] = terrainCodec.encodeTile(EMPTY);
      }
    }
  }

  private World(int width, int height, byte[] terrain) {
    this.width = width;
    this.height = height;
    size = width * height;
    this.terrain = terrain;
    terrainCodec = new TerrainCodec();
  }

  public static World of(int width, int height) {
    validateDimensions(width, height);
    return new World(width, height);
  }

  public static World of(int width, int height, byte[] terrain) {
    validateDimensions(width, height);
    return new World(width, height, terrain);
  }

  public static World of(WorldTerrainSettings worldTerrainSettings, byte[] terrain) {
    if (worldTerrainSettings == null) {
      throw new IllegalArgumentException("Empty worldTerrain Settings");
    }
    validateDimensions(worldTerrainSettings.width(), worldTerrainSettings.height());
    return new World(worldTerrainSettings.width(), worldTerrainSettings.height(), terrain);
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public Tile getTile(int x, int y) {
    return terrainCodec.decode(terrain[y * width + x]);
  }

  public byte[] getTerrain() {
    return terrain;
  }

  public byte getEncodedTile(int x, int y) {
    return terrain[y * width + x];
  }

  private static void validateDimensions(int width, int height) {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException(
          String.format("Invalid world dimensions: W=%s, H=%s", width, height));
    }
  }
}
