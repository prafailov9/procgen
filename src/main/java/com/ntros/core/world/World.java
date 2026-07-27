package com.ntros.core.world;

public class World {

    private final int width, height;
    private final int size;
    // flat array representing a 2d grid, for faster cache access
    private final byte[] terrain;
    private final TerrainTileEncoder terrainTileEncoder;

    private World(int width, int height, long seed) {
        this.width = width;
        this.height = height;
        size = width * height;
        terrain = new byte[size];

        terrainTileEncoder = new TerrainTileEncoder(seed);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                terrain[y * width + x] = terrainTileEncoder.getRandomEncodedTerrainTile();
            }
        }
    }

    private World(int width, int height, byte[] terrain, long seed) {
        this.width = width;
        this.height = height;
        size = width * height;
        this.terrain = terrain;
        terrainTileEncoder = new TerrainTileEncoder(seed);

    }

    public static World of(int width, int height, long seed) {
        validateDimensions(width, height);
        return new World(width, height, seed);
    }

    public static World of(int width, int height, byte[] terrain, long seed) {
        validateDimensions(width, height);
        return new World(width, height, terrain, seed);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Tile getTile(int x, int y) {
        return terrainTileEncoder.getTiles().get(terrain[y * width + x]);
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
