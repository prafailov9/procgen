package com.ntros.core.world;

import com.ntros.core.world.terrain.TerrainCodec;
import com.ntros.core.world.terrain.Tile;
import com.ntros.core.world.terrain.WorldTerrainSettings;

import java.util.Arrays;

import static com.ntros.core.world.terrain.Tile.EMPTY;

/**
 * Representation of the 2D Simulation World. Updated by the StateProcessor, displayed by the
 * StateRenderer. Houses all visible and/or interactable game objects.
 */
public class World {

  private final int width, height, size;
  // flat array representing a 2d grid, for faster cache access
  // terrain-level data
  private final byte[] terrain;
  private final byte[] elevation;
  private final byte[] moisture;
  private final byte[] lightLevel;
  // TODO: add other game objects Struct-of-Arrays style.
  private final TerrainCodec terrainCodec;

  private int entityId;

  private World(int width, int height) {
    this.width = width;
    this.height = height;
    size = width * height;
    terrain = new byte[size];
    elevation = new byte[size];
    moisture = new byte[size];
    lightLevel = new byte[size];

    terrainCodec = new TerrainCodec();
    Arrays.fill(terrain, terrainCodec.encodeTile(EMPTY));
  }

  private World(int width, int height, byte[] terrain) {
    this.width = width;
    this.height = height;
    size = width * height;
    this.terrain = terrain;
    elevation = new byte[size];
    moisture = new byte[size];
    lightLevel = new byte[size];
    terrainCodec = new TerrainCodec();
  }

  public static World of(int width, int height) {
    validateDimensions(width, height);
    return new World(width, height);
  }

  public static World of(int width, int height, byte[] terrain) {
    validateDimensions(width, height);
    validateTerrain(width, height, terrain);
    return new World(width, height, terrain);
  }

  public static World of(WorldTerrainSettings worldTerrainSettings, byte[] terrain) {
    if (worldTerrainSettings == null) {
      throw new IllegalArgumentException("Empty worldTerrain Settings");
    }
    validateDimensions(worldTerrainSettings.width(), worldTerrainSettings.height());
    validateTerrain(worldTerrainSettings.width(), worldTerrainSettings.height(), terrain);
    return new World(worldTerrainSettings.width(), worldTerrainSettings.height(), terrain);
  }

  public int createEntity() {
    return entityId++;
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

  public int getSize() {
    return size;
  }

  public byte[] getElevation() {
    return elevation;
  }

  public byte[] getMoisture() {
    return moisture;
  }

  public byte[] getLightLevel() {
    return lightLevel;
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

  private static void validateTerrain(int width, int height, byte[] terrain) {
    if (terrain == null || terrain.length != width * height) {
      throw new IllegalArgumentException("Terrain length must equal width * height");
    }
  }
}
