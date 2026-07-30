package com.ntros.core.processor;

import com.ntros.AppConstants;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WorldStateProcessorTest {

  private World world;
  private TickingClock clock = SimClock.ofDefaultTimeScale();
  private Channel channel = new CommandChannel(100);
  private AtomicReference<WorldSnapshot> latestSnapshot = new AtomicReference<>();
  private CancellationToken token = new CancellationToken();

  private WorldStateProcessor processor;
  private Thread procThread;

  @BeforeEach
  public void setup() {
    world = generateWorld();
    processor = new WorldStateProcessor(world, clock, channel, latestSnapshot, token);
    procThread = new Thread(processor, "proc1");
  }

  @Test
  public void testSpeed() throws InterruptedException {
    int seed = 55;
    SimStats simStats = processor.getSimStats();
    Random random = new Random(seed);
    List<SimulationSpeed> speeds = List.of(SimulationSpeed.values());
    procThread.start();
    for (int i = 1; i <= 10; i++) {
      Thread.sleep(10);
      ChangeSpeedCommand command = ChangeSpeedCommand.of(speeds.get(random.nextInt(speeds.size())));
      channel.tryOffer(command);
    }
    stop();

    System.out.println(simStats);
  }

  private void stop() throws InterruptedException {
    token.cancel();
    procThread.interrupt();
    procThread.join();
  }

  private World generateWorld() {
    WorldTerrainSettings worldTerrainSettings =
        new WorldTerrainSettings(AppConstants.WIDTH, AppConstants.HEIGHT, 1);
    TerrainGenerationSettings terrainGenerationSettings =
        new TerrainGenerationSettings(worldTerrainSettings, NoiseSettings.ofDefault());
    NoiseTerrainGenerator noiseTerrainGenerator =
        new NoiseTerrainGenerator(terrainGenerationSettings);

    var terrain = noiseTerrainGenerator.generateTerrain();

    return World.of(worldTerrainSettings.width(), worldTerrainSettings.height(), terrain);
  }
}
