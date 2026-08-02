package com.ntros.generator.fastnoiselite;

/**
 * Noise parameters for terrain generation. What controls what:
 *
 * <pre>
 * WATER BODIES are elevation, not moisture: the classifier maps elevation &lt; 0.21 to deep water
 * and &lt; 0.38 to shallow water. Two knobs shape them:
 *   - elevationFrequency: the size/count of water bodies. Lower = fewer, larger oceans and
 *     continents (longer wavelength); higher = many small lakes. THIS is the water-frequency knob.
 *   - TerrainClassifier's elevation thresholds: the total water FRACTION of the map.
 *
 * OCTAVES (all of them) only add finer detail on top of the base wavelength: more elevation
 * octaves = more ragged coastlines and rougher terrain, but the same number of water bodies.
 * The moisture pair never touches water at all - moisture only picks GRASS vs SAND/FOREST on
 * land tiles. Ridged noise adds mountain ridgelines at high elevations.
 * </pre>
 */
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
