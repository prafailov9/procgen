package com.ntros.generator;

import com.ntros.core.world.terrain.TerrainClassifier;
import com.ntros.core.world.terrain.TerrainGenerationSettings;
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
  private final float[] elevation;
  private final float[] moisture;
  private final TerrainClassifier terrainClassifier;

  public NoiseTerrainGenerator(TerrainGenerationSettings settings) {
    validateSettings(settings);
    var terrainSettings = settings.worldTerrainSettings();
    var dimensions = terrainSettings.dimensions2d();
    this(
        dimensions.width(),
        dimensions.height(),
        terrainSettings.seed(),
        settings.noiseSettings());
  }

  public NoiseTerrainGenerator(int width, int height, long seed, NoiseSettings settings) {
    this.width = width;
    this.height = height;
    this.seed = seed;
    terrainClassifier = new TerrainClassifier();
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
    elevation = new float[size];
    moisture = new float[size];
  }

  public byte[] generateTerrain() {
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int idx = y * width + x;
        float e = normalize(elevationNoise.GetNoise(x, y));
        float m = normalize(moistureNoise.GetNoise(x, y));
        float r = normalize(ridgedNoise.GetNoise(x, y));
        float t = smoothstep(0.70f, 0.95f, e); // 0 in lowlands, ramps toward peaks
        float combined = e + (r - 0.5f) * 0.25f * t; // adds ridgelines, keeps peaks high
        terrain[idx] = terrainClassifier.classify(combined, m);
        elevation[idx] = e;
        moisture[idx] = m;
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

  private FastNoiseLite buildNoise(
      long seed, FastNoiseLite.FractalType fractalType, float frequency, int octaves) {
    var noise = new FastNoiseLite((int) seed);
    noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
    noise.SetFractalType(fractalType);
    noise.SetFrequency(frequency); // lower -> bigger landmasses
    noise.SetFractalOctaves(octaves);
    return noise;
  }

  private static void validateSettings(TerrainGenerationSettings settings) {
    if (settings == null) {
      throw new IllegalArgumentException("Empty terrain gen settings");
    }
    if (settings.worldTerrainSettings() == null) {
      throw new IllegalArgumentException("Empty World Terrain settings");
    }

    if (settings.noiseSettings() == null) {
      throw new IllegalArgumentException("Empty noise gen settings");
    }
    var terrainSettings = settings.worldTerrainSettings();
    var dimensions = terrainSettings.dimensions2d();
    if (dimensions == null) {
      throw new IllegalArgumentException("Empty terrain dimensions");
    }
    if (dimensions.width() <= 0 || dimensions.height() <= 0) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid terrain width/height. width: %s; height: %s",
              dimensions.width(), dimensions.height()));
    }
  }
}
