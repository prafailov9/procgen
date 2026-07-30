package com.ntros.generator.fastnoiselite;

public record NoiseSettings(
    float elevationFrequency,
    int elevationOctaves,
    float moistureFrequency,
    int moistureOctaves,
    float ridgedFrequency,
    int ridgedOctaves) {

  public static NoiseSettings ofDefault() {
    return new NoiseSettings(0.0025f, 5, 0.0032f, 3, 0.005f, 5);
  }
}
