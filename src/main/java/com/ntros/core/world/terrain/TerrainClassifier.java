package com.ntros.core.world.terrain;

import com.ntros.generator.Terrain;

import static com.ntros.core.world.terrain.Tile.*;
import static com.ntros.core.world.terrain.Tile.FOREST;
import static com.ntros.core.world.terrain.Tile.GRASS;
import static com.ntros.core.world.terrain.Tile.HILL;
import static com.ntros.core.world.terrain.Tile.MOUNTAIN;
import static com.ntros.core.world.terrain.Tile.SAND;

public final class TerrainClassifier {

  private final TerrainCodec terrainCodec;

  public TerrainClassifier() {
    this.terrainCodec = new TerrainCodec();
  }

  public byte classify(float elevation, float moisture) {
    if (elevation < 0.29f) return terrainCodec.encodeTile(DEEP_WATER);
    if (elevation < 0.38f) return terrainCodec.encodeTile(SHALLOW_WATER);
    if (elevation < 0.41f)
      return moisture < 0.45f ? terrainCodec.encodeTile(GRASS) : terrainCodec.encodeTile(SAND);
    if (elevation < 0.75f)
      return moisture < 0.45f
          ? terrainCodec.encodeTile(GRASS)
          : terrainCodec.encodeTile(FOREST); // lowland
    if (elevation < 0.88f) return terrainCodec.encodeTile(HILL);
    return terrainCodec.encodeTile(MOUNTAIN);
  }

  public float spawnChance(int x, int y, Terrain terrain) {
    int idx = y * terrain.dimensions2d().width() + x;

    Tile tile = terrainCodec.decode(terrain.tiles()[idx]);
    // TODO: figure out how to make moisture and elevation matter for spawn
    //    float moisture = terrain.moisture()[idx];
    //    float elevation = terrain.elevation()[idx];
    switch (tile) {
      case SAND -> {
        return 0.21f;
      }
      case FOREST -> {
        return 0.52f;
      }
      case GRASS -> {
        return 0.55f;
      }

      case HILL -> {
        return 0.18f;
      }

      case MOUNTAIN -> {
        return 0.10f;
      }

      case null, default -> {
        return 0.00f;
      }
    }
  }

}
