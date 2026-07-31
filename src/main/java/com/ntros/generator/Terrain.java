package com.ntros.generator;

import com.ntros.graphics.rendering.data.Dimensions2d;

public record Terrain(byte[] tiles, float[] elevation, float[] moisture, Dimensions2d dimensions2d) {}
