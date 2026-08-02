package com.ntros.core.world;

import com.ntros.core.channel.Channel;
import com.ntros.core.channel.ConcurrentChannel;
import com.ntros.core.world.terrain.TerrainCodec;
import com.ntros.core.world.terrain.Tile;
import com.ntros.core.world.terrain.WorldTerrainSettings;
import com.ntros.core.ecs.store.CreatureStore;
import com.ntros.core.ecs.store.LifecycleRequests;
import com.ntros.core.world.snapshot.StatsSnapshot;
import com.ntros.generator.Terrain;

/**
 * Representation of the 2D Simulation World. Updated by the StateProcessor, displayed by the
 * StateRenderer. Houses all visible and/or interactable game objects.
 */
public final class World {

  private final int width, height, size;
  private final long seed;
  // flat array representing a 2d grid, for faster cache access
  // tiles-level data
  private final byte[] terrain;
  private final float[] elevation;

  /** contributes to biomass growth */
  private final float[] moisture;

  private final float[] biomass;
  private final byte[] lightLevel;
  private final CreatureStore creatureStore;
  // deferred birth/death queues, drained by LifecycleSystem each tick
  private final LifecycleRequests lifecycleRequests = new LifecycleRequests();
  private final WorldStats worldStats;
  // per-tile predator influence map, rebuilt each tick by DangerGridSystem
  private final DangerGrid dangerGrid = new DangerGrid();
  // starts non-null so a world can be published before AnalyticsSystem's first sample
  private StatsSnapshot latestStats = StatsSnapshot.empty();
  private final TerrainCodec terrainCodec;
  private final Channel<StatsSnapshot> statsChannel = new ConcurrentChannel<>(1024);

  private World(
      int width,
      int height,
      long seed,
      byte[] terrain,
      float[] biomass,
      CreatureStore creatureStore,
      WorldStats worldStats) {
    this.width = width;
    this.height = height;
    this.seed = seed;
    size = width * height;
    this.terrain = terrain;
    this.biomass = biomass;
    elevation = new float[size];
    moisture = new float[size];
    lightLevel = new byte[size];
    this.creatureStore = creatureStore;
    this.worldStats = worldStats;

    terrainCodec = new TerrainCodec();
  }

  public static World of(
      WorldTerrainSettings worldTerrainSettings,
      Terrain terrain,
      float[] biomass,
      CreatureStore creatureStore,
      WorldStats worldStats) {
    if (worldTerrainSettings == null) {
      throw new IllegalArgumentException("Empty worldTerrain Settings");
    }
    var dimensions = worldTerrainSettings.dimensions2d();
    if (dimensions == null) {
      throw new IllegalArgumentException("Empty dimensions");
    }
    if (creatureStore == null) {
      throw new IllegalArgumentException("Empty creature store");
    }

    validateDimensions(dimensions.width(), dimensions.height());
    validateTerrain(dimensions.width(), dimensions.height(), terrain.tiles());
    return new World(
        dimensions.width(),
        dimensions.height(),
        worldTerrainSettings.seed(),
        terrain.tiles(),
        biomass,
        creatureStore,
        worldStats);
  }

  public WorldStats getWorldStats() {
    return worldStats;
  }

  public DangerGrid getDangerGrid() {
    return dangerGrid;
  }

  public Channel<StatsSnapshot> getStatsChannel() {
    return statsChannel;
  }

  /**
   * Latest immutable analytics value, replaced by AnalyticsSystem on sample boundaries and read by
   * WorldSnapshot.of at publish time. Written and read only on the sim thread; it reaches the EDT
   * through the snapshot's AtomicReference, which supplies the happens-before edge.
   */
  public StatsSnapshot getLatestStats() {
    return latestStats;
  }

  public void publishStats(StatsSnapshot stats) {
    latestStats = stats;
    statsChannel.forceOffer(stats);
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public long getSeed() {
    return seed;
  }

  public Tile getTile(int x, int y) {
    return terrainCodec.decode(terrain[y * width + x]);
  }

  public byte[] getTerrain() {
    return terrain;
  }

  public int getSize() {
    return size;
  }

  public float[] getElevation() {
    return elevation;
  }

  public float[] getMoisture() {
    return moisture;
  }

  public byte[] getLightLevel() {
    return lightLevel;
  }

  public float[] getBiomass() {
    return biomass;
  }

  public CreatureStore getCreatureStore() {
    return creatureStore;
  }

  public LifecycleRequests getLifecycleRequests() {
    return lifecycleRequests;
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

  private static void validateTerrain(int width, int height, byte[] terrain) {
    if (terrain == null || terrain.length != width * height) {
      throw new IllegalArgumentException("Terrain length must equal width * height");
    }
  }
}
