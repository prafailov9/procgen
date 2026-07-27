package com.ntros.generator;

import static com.ntros.core.world.Tile.*;

import com.ntros.core.world.Tile;
import com.ntros.generator.fastnoiselite.FastNoiseLite;
import com.ntros.generator.fastnoiselite.NoiseSettings;

public class NoiseTerrainGenerator {

  private final int width;
  private final int height;
  private final long seed;

  private final FastNoiseLite elevationNoise;
  private final FastNoiseLite moistureNoise;
  private final FastNoiseLite ridgedNoise;

  private final byte[] terrain;

  public NoiseTerrainGenerator(int width, int height, long seed, NoiseSettings settings) {
    this.width = width;
    this.height = height;
    this.seed = seed;

    elevationNoise =
        buildNoise(
            seed,
            FastNoiseLite.FractalType.FBm,
            settings.elevationFrequency(),
            settings.elevationOctaves());

    moistureNoise =
        buildNoise(
            seed + 1,
            FastNoiseLite.FractalType.FBm,
            settings.moistureFrequency(),
            settings.moistureOctaves());

    ridgedNoise =
        buildNoise(
            seed + 2,
            FastNoiseLite.FractalType.Ridged,
            settings.ridgedFrequency(),
            settings.ridgedOctaves());

    int size = width * height;
    terrain = new byte[size];
  }

  public byte[] generateTerrain() {
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        float e = normalize(elevationNoise.GetNoise(x, y));
        float m = normalize(moistureNoise.GetNoise(x, y));
        float r = normalize(ridgedNoise.GetNoise(x, y));
        float t = smoothstep(0.70f, 0.95f, e); // 0 in lowlands, ramps toward peaks
        float combined = e + (r - 0.5f) * 0.25f * t; // adds ridgelines, keeps peaks high
        terrain[y * width + x] = classify(combined, m);
      }
    }
    return terrain;
  }

  private static float smoothstep(float a, float b, float x) {
    float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
    return t * t * (3 - 2 * t);
  }

  private float normalize(float n) {
    return Math.max(0f, Math.min(1f, (n + 1f) * 0.5f));
  }

  private byte classify(float elevation, float moisture) {
    if (elevation < 0.18) return encodeTile(DEEP_WATER);
    if (elevation < 0.38f) return encodeTile(SHALLOW_WATER);
    if (elevation < 0.41f)
      return moisture < 0.45f ? encodeTile(GRASS) : encodeTile(SAND);
    if (elevation < 0.75f)
      return moisture < 0.45f ? encodeTile(GRASS) : encodeTile(FORREST); // lowland
    if (elevation < 0.88f) return encodeTile(HILL);
    return encodeTile(MOUNTAIN);
  }

  private byte encodeTile(Tile tile) {
    return (byte) tile.ordinal();
  }

  private FastNoiseLite buildNoise(
      long seed, FastNoiseLite.FractalType fractalType, float frequency, int octaves) {
    var noise = new FastNoiseLite((int) seed);
    noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
    noise.SetFractalType(fractalType);
    noise.SetFrequency(frequency); // lower -> bigger landmasses
    noise.SetFractalOctaves(octaves);
    return noise;
  }
}
