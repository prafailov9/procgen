package com.ntros.core.world.terrain;

import static com.ntros.core.world.terrain.Tile.*;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerrainCodec {

  private static final Logger log = LoggerFactory.getLogger(TerrainCodec.class);

  // allowed tiles in the sim
  private static final List<Tile> TILES_ALLOWLIST;

  static {
    List<Tile> tiles = new ArrayList<>();
    for (var t : Tile.values()) {
      if (!isTileAllowedInSim(t)) {
        continue;
      }
      tiles.add(t);
    }
    TILES_ALLOWLIST = List.copyOf(tiles);
  }

  private static boolean isTileAllowedInSim(Tile tile) {
    return tile != EMPTY;
  }

  public TerrainCodec() {}

  public byte encodeTile(Tile tile) {
    return (byte) tile.ordinal();
  }

  public Tile decode(byte encoded) {
    int idx = Byte.toUnsignedInt(encoded);

    if (idx >= TILES_ALLOWLIST.size()) {
      throw new IllegalArgumentException("Unknown encoded tile: " + idx);
    }

    return TILES_ALLOWLIST.get(idx);
  }
}
