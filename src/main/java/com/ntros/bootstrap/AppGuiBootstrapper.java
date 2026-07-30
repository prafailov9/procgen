package com.ntros.bootstrap;

import com.ntros.AppConstants;
import com.ntros.core.CancellationToken;
import com.ntros.core.SimulationController;
import com.ntros.core.processor.WorldStateProcessor;
import com.ntros.core.channel.CommandChannel;
import com.ntros.core.clock.SimClock;
import com.ntros.core.clock.TickingClock;
import com.ntros.core.control.ChannelIntentTranslator;
import com.ntros.core.control.SwappableIntentTranslator;
import com.ntros.core.world.World;
import com.ntros.core.world.terrain.TerrainGenerationSettings;
import com.ntros.core.world.WorldSnapshot;
import com.ntros.generator.NoiseTerrainGenerator;
import com.ntros.graphics.rendering.AppGuiRunner;
import com.ntros.graphics.rendering.StateUIRenderer;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicReference;

import static com.ntros.graphics.ScreenType.SIMULATION;

/**
 * Wires the GUI screens to the simulation runtime: world setup requests generation, generation
 * completion starts the sim and switches to the simulation screen.
 */
public final class AppGuiBootstrapper {
  private static final int RENDERER_DELAY_MS = 16;
  private static final int COMMAND_QUEUE_CAPACITY = 1024;

  // stable handle the GUI holds forever; retargeted at each run's channel
  private final SwappableIntentTranslator intentTranslator = new SwappableIntentTranslator();

  private AppGuiRunner appGuiRunner;
  private SimulationController simulationController;

  public void bootstrapApplication() {
    appGuiRunner =
        new AppGuiRunner(
            AppConstants.WIDTH, AppConstants.HEIGHT, this::onGenerationRequested, intentTranslator);
    appGuiRunner.startGuiApp();
  }

  /** Stops the running simulation, if any. Safe to call from a shutdown hook. */
  public void shutdown() throws InterruptedException {
    if (simulationController != null) {
      simulationController.stop();
    }
  }

  // This is a callback that gets passed down multiple steps. Can get messy when multiple different
  // generations are needed.
  // Generation might need to be a StateProcessor concern or a different abstraction.
  // TODO: explore different generation triggering options
  private void onGenerationRequested(TerrainGenerationSettings settings) {
    // TODO: figure out if you need a generation thread
    Thread generationThread =
        new Thread(
            () -> {
              try {
                // TODO: create multiple generations for different objects in the world
                NoiseTerrainGenerator generator = new NoiseTerrainGenerator(settings);
                byte[] terrain = generator.generateTerrain();
                World world = World.of(settings.worldTerrainSettings(), terrain);
                // submit start on the EDT
                SwingUtilities.invokeLater(() -> startSimulation(world));
              } catch (Exception e) {
                SwingUtilities.invokeLater(
                    () -> appGuiRunner.getWorldSetupPanel().showGenerationError(e));
              }
            },
            "world-gen");
    generationThread.start();
  }

  // start spinning once a world is generated
  private void startSimulation(World world) {
    stopCurrentSimulation();
    // fresh token per run, cannot reuse cancelled tokens
    CancellationToken token = new CancellationToken();
    // world state data [loop -> renderer]
    AtomicReference<WorldSnapshot> latestSnapshot = new AtomicReference<>();
    // Player commands [renderer -> loop]
    CommandChannel channel = new CommandChannel(COMMAND_QUEUE_CAPACITY);
    // measures wall time
    TickingClock clock = SimClock.ofDefaultTimeScale();
    // The State Updater
    WorldStateProcessor worldStateProcessor =
        new WorldStateProcessor(world, clock, channel, latestSnapshot, token);
    // The UI Updater
    StateUIRenderer renderer = new StateUIRenderer(appGuiRunner.getWorldSimPanel(), latestSnapshot);
    // Handles the Simulation lifecycle
    simulationController =
        new SimulationController(worldStateProcessor, renderer, RENDERER_DELAY_MS, token);
    simulationController.start();

    intentTranslator.setDelegate(new ChannelIntentTranslator(channel));
    appGuiRunner.getWorldSetupPanel().setGenerating(false);
    appGuiRunner.getScreenController().show(SIMULATION);
  }

  private void stopCurrentSimulation() {
    if (simulationController == null) {
      return;
    }
    intentTranslator.setDelegate(null); // stop routing input before tearing the run down
    try {
      simulationController.stop();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    simulationController = null;
  }
}
