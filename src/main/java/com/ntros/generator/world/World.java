package com.ntros.generator.world;

import com.ntros.generator.Tile;
import java.util.ArrayList;
import java.util.List;

public class World {

  private final int width, height;
  // flat array representing a 2d grid, for faster cache access
  private List<Tile> terrain = new ArrayList<>();

  private World(int width, int height) {
    this.width = width;
    this.height = height;
  }

  private World(int width, int height, List<Tile> terrain) {
    this(width, height);
    this.terrain = terrain;
  }

  public static World of(int width, int height, List<Tile> terrain) {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException(
          String.format("Invalid world dimensions: W=%s, H=%s", width, height));
    }
    return new World(width, height, terrain);
  }

  public List<Tile> getTerrain() {
    return terrain;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public Tile getTile(int x, int y) {
    return terrain.get(y * width + x);
  }

  public String getName() {
    return "proc-gen";
  }
}
