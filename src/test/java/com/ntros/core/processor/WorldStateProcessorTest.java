package com.ntros.core.processor;

import com.ntros.core.updater.Actor;
import com.ntros.core.updater.StateActor;
import com.ntros.ecs.system.BiomassGrowthSystem;
import com.ntros.generator.BiomassGenerator;
import com.ntros.graphics.rendering.data.Dimensions2d;
import com.ntros.core.CancellationToken;
import com.ntros.core.SimulationSpeed;
import com.ntros.core.channel.Channel;
import com.ntros.core.channel.CommandChannel;
import com.ntros.core.clock.SimClock;
import com.ntros.core.clock.TickingClock;
import com.ntros.core.command.ChangeSpeedCommand;
import com.ntros.core.world.World;
import com.ntros.core.world.WorldSnapshot;
import com.ntros.core.world.terrain.TerrainGenerationSettings;
import com.ntros.core.world.terrain.WorldTerrainSettings;
import com.ntros.generator.NoiseTerrainGenerator;
import com.ntros.generator.fastnoiselite.NoiseSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

class WorldStateProcessorTest {

  private World world;
  private TickingClock clock = SimClock.ofDefaultTimeScale();
  private Actor actor;
  private Channel channel = new CommandChannel(100);
  private AtomicReference<WorldSnapshot> latestSnapshot = new AtomicReference<>();
  private CancellationToken token = new CancellationToken();
  private static final long SEED = 55;
  private WorldStateProcessor processor;
  private Thread procThread;

  @BeforeEach
  public void setup() {
    world = generateWorld(SEED);
    actor = new StateActor(List.of(new BiomassGrowthSystem(world.getSeed())));
    processor = new WorldStateProcessor(world, clock, actor, channel, latestSnapshot, token);
    procThread = new Thread(processor, "proc1");
  }

  @Test
  public void runProc_verifySimStatsUpdated() throws InterruptedException {
    SimStats simStats = processor.getSimStats();
    Random random = new Random(SEED);
    List<SimulationSpeed> speeds = List.of(SimulationSpeed.values());
    procThread.start();
    for (int i = 1; i <= 10; i++) {
      Thread.sleep(10);
      ChangeSpeedCommand command = ChangeSpeedCommand.of(speeds.get(random.nextInt(speeds.size())));
      channel.tryOffer(command);
    }
    stopProc();
    Assertions.assertTrue(simStats.getElapsedRealTime() > 0);
    Assertions.assertTrue(simStats.getLastPublishedTick() > -1);
    Assertions.assertTrue(simStats.getLastPublishTimeNanos() > 0);
    Assertions.assertTrue(simStats.getTimeBucket() != 0.00d);
  }

  private void stopProc() throws InterruptedException {
    token.cancel();
    procThread.interrupt();
    procThread.join();
  }

  private World generateWorld(long seed) {
    Dimensions2d d = Dimensions2d.ofSmallWorld();
    WorldTerrainSettings worldTerrainSettings = new WorldTerrainSettings(d, seed);
    TerrainGenerationSettings terrainGenerationSettings =
        new TerrainGenerationSettings(worldTerrainSettings, NoiseSettings.ofDefault());
    NoiseTerrainGenerator noiseTerrainGenerator =
        new NoiseTerrainGenerator(terrainGenerationSettings);

    var terrain = noiseTerrainGenerator.generateTerrain();
    var biomass = new BiomassGenerator(terrain, seed + 1).generateBiomass();
    return World.of(worldTerrainSettings, terrain, biomass);
  }
}
