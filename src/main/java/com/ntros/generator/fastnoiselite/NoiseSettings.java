package com.ntros.generator.fastnoiselite;

public record NoiseSettings(float elevationFrequency,
                            int elevationOctaves,
                            float moistureFrequency,
                            int moistureOctaves,
                            float ridgedFrequency,
                            int ridgedOctaves) {
}
