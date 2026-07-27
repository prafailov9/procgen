package com.ntros.core.world;

import java.util.List;
import java.util.Random;

public class TerrainTileEncoder {

    private final Random random;
    private final List<Tile> tiles = List.of(Tile.values());
    private static final int TILE_COUNT = Tile.values().length - 1;


    public TerrainTileEncoder(long seed) {
        random = new Random(seed);
    }

    public List<Tile> getTiles () {
        return tiles;
    }

    public Tile getRandomTerrainTile() {
        return tiles.get(random.nextInt(TILE_COUNT));
    }

    public byte getRandomEncodedTerrainTile() {
        Tile tile = getRandomTerrainTile();
        return (byte) tile.ordinal();
    }


}
