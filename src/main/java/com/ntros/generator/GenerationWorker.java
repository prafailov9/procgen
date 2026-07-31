package com.ntros.generator;

import com.ntros.core.Lifecycle;
import com.ntros.core.world.World;
import com.ntros.core.world.terrain.TerrainGenerationSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class GenerationWorker implements Lifecycle {
  private static final Logger log = LoggerFactory.getLogger(GenerationWorker.class);
  private final Thread gen;
  private final TerrainGenerationSettings terrainGenerationSettings;
  private final CompletableFuture<World> worldPromise;

  public GenerationWorker(TerrainGenerationSettings terrainGenerationSettings) {
    this.terrainGenerationSettings = terrainGenerationSettings;
    worldPromise = new CompletableFuture<>();
    gen = new Thread(this::generate, "world-gen-1");
    log.info("Created world-gen-1 Thread");
  }

  @Override
  public void start() {
    log.info("Starting world-gen-1...");
    gen.start();
  }

  private void generate() {
    try {
      log.info("starting generation with settings: {}", terrainGenerationSettings);
      var terrain = new NoiseTerrainGenerator(terrainGenerationSettings).generateTerrain();
      var biomass = new BiomassGenerator(terrain).generateBiomass();
      // spawner throws IOOB. TODO: fix
      //      var creatureStore = new CreatureSpawner(terrain).spawnEntities();
      worldPromise.complete(
          World.of(terrainGenerationSettings.worldTerrainSettings(), terrain, biomass));
      log.info("Gen finished");
    } catch (Throwable ex) {
      worldPromise.completeExceptionally(ex);
    }
  }

  public CompletableFuture<World> deliver() {
    log.info("Delivering world...");
    return worldPromise;
  }

  // stopping will be important when generation becomes more complex
  @Override
  public void stop() throws InterruptedException {
    gen.join();
  }
}
