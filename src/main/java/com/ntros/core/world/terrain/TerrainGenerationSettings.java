package com.ntros.core.world.terrain;

import com.ntros.generator.fastnoiselite.NoiseSettings;

public record TerrainGenerationSettings(
        WorldTerrainSettings worldTerrainSettings,
        NoiseSettings noiseSettings
) {}