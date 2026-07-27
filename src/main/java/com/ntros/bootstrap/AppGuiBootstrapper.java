package com.ntros.bootstrap;

import com.ntros.MainSettings;
import com.ntros.core.CancellationToken;
import com.ntros.core.SimulationController;
import com.ntros.core.WorldStateLoop;
import com.ntros.core.channel.CommandChannel;
import com.ntros.core.clock.SimClock;
import com.ntros.core.clock.TickingClock;
import com.ntros.core.world.World;
import com.ntros.core.world.WorldGenerationSettings;
import com.ntros.core.world.WorldSnapshot;
import com.ntros.generator.NoiseTerrainGenerator;
import com.ntros.graphics.ScreenType;
import com.ntros.graphics.rendering.AppGuiRunner;
import com.ntros.graphics.rendering.StateRenderer;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Wires the GUI screens to the simulation runtime: world setup requests generation, generation
 * completion starts the sim and switches to the simulation screen.
 */
public final class AppGuiBootstrapper {
  private static final int RENDERER_DELAY_MS = 16;
  private static final int COMMAND_QUEUE_CAPACITY = 1024;

  private AppGuiRunner appGuiRunner;
  private SimulationController simulationController;

  public void bootstrapApplication() {
    appGuiRunner =
        new AppGuiRunner(MainSettings.WIDTH, MainSettings.HEIGHT, this::onGenerationRequested);
    appGuiRunner.startGuiApp();
  }

  /** Stops the running simulation, if any. Safe to call from a shutdown hook. */
  public void shutdown() throws InterruptedException {
    if (simulationController != null) {
      simulationController.stop();
    }
  }

  /** Called on the EDT when the user submits the world-setup form. */
  private void onGenerationRequested(WorldGenerationSettings settings) {
    // TODO: create multiple generations for different objects in the world
    Thread generationThread =
        new Thread(
            () -> {
              try {
                NoiseTerrainGenerator generator =
                    new NoiseTerrainGenerator(
                        settings.width(),
                        settings.height(),
                        settings.seed(),
                        settings.noiseSettings());
                byte[] terrain = generator.generateTerrain();
                World world =
                    World.of(settings.width(), settings.height(), terrain, settings.seed());
                SwingUtilities.invokeLater(() -> startSimulation(world));
              } catch (Exception e) {
                SwingUtilities.invokeLater(
                    () -> appGuiRunner.getWorldSetupPanel().showGenerationError(e));
              }
            },
            "world-gen");
    generationThread.start();
  }

  /** Runs on the EDT once generation finishes. */
  private void startSimulation(World world) {
    stopCurrentSimulation();

    // fresh token per run, cannot reuse cancelled tokens
    CancellationToken token = new CancellationToken();
    AtomicReference<WorldSnapshot> latestSnapshot = new AtomicReference<>();
    CommandChannel channel = new CommandChannel(COMMAND_QUEUE_CAPACITY);
    TickingClock clock = SimClock.ofDefaultTimeScale();
    WorldStateLoop worldStateLoop =
        new WorldStateLoop(world, clock, channel, latestSnapshot, token);
    StateRenderer renderer = new StateRenderer(appGuiRunner.getWorldSimPanel(), latestSnapshot);

    simulationController =
        new SimulationController(worldStateLoop, renderer, RENDERER_DELAY_MS, token);
    simulationController.start();

    appGuiRunner.getWorldSetupPanel().setGenerating(false);
    appGuiRunner.getScreenController().show(ScreenType.SIMULATION);
  }

  private void stopCurrentSimulation() {
    if (simulationController == null) {
      return;
    }
    try {
      simulationController.stop();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    simulationController = null;
  }
}
