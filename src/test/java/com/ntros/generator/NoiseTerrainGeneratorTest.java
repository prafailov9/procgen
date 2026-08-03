package com.ntros.generator;

import com.ntros.generator.fastnoiselite.FastNoiseLite;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class NoiseTerrainGeneratorTest {

  private static final Logger log = LoggerFactory.getLogger(NoiseTerrainGeneratorTest.class);
  private float[] spheric;

  @Test
  public void heighMapTest() {
    long seed = new Random().nextLong();
    log.info("Seed: {}", seed);
    int width = 10;
    int height = 10;
    int startSize = 13;
    double featureSize = startSize;
    int endSize = 8;
    int worldSize = width * height;
    double max = 0.0F;
    double min = 1.0F;

    spheric = new float[worldSize];
    OpenSimplexNoise noise = new OpenSimplexNoise(seed);

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        double v = 0.0;
        double size = featureSize;

        for (int i = 1; size >= (double) endSize; ++i) {
          double factorX = (double) x / size;
          double factorY = (double) y / size;
          v += noise.eval(factorX, factorY) / (double) i;
          size /= 2.0F;
        }

        spheric[y * width + x] = (float) v;
        if (v > max) {
          max = v;
        }
        if (v < min) {
          min = v;
        }
      }
    }

    double range = max - min;
    double d = (double) 1.0F / range;

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        double v = spheric[y * width + x];
        v -= min;
        v *= d;
        spheric[y * width + x] = (float) v;
      }
    }
  }

  // public HeightMap(int width, int height, int startSize, int endSize) {
  //      OpenSimplexNoise noise = new OpenSimplexNoise(RND.rLong());
  //      double FEATURE_SIZE = (double)startSize;
  //      this.spheric = new float[height][width];
  //      double max = (double)0.0F;
  //      double min = (double)1.0F;
  //
  //      for(int y = 0; y < height; ++y) {
  //         for(int x = 0; x < width; ++x) {
  //            double v = (double)0.0F;
  //            double size = FEATURE_SIZE;
  //
  //            for(int i = 1; size >= (double)endSize; ++i) {
  //               v += noise.eval((double)x / size, (double)y / size) / (double)i;
  //               size /= (double)2.0F;
  //            }
  //
  //            this.spheric[y][x] = (float)v;
  //            if (v > max) {
  //               max = v;
  //            }
  //
  //            if (v < min) {
  //               min = v;
  //            }
  //         }
  //      }

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
