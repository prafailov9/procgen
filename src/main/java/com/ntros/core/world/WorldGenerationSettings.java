package com.ntros.core.world;

import com.ntros.generator.fastnoiselite.NoiseSettings;

public record WorldGenerationSettings(
        int width,
        int height,
        long seed,
        NoiseSettings noiseSettings
) {}