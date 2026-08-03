package com.ntros.bootstrap;

import static com.ntros.graphics.ScreenType.SIMULATION;

import com.ntros.AppConstants;
import com.ntros.core.CancellationToken;
import com.ntros.core.SimulationController;
import com.ntros.core.channel.CommandChannel;
import com.ntros.core.clock.SimClock;
import com.ntros.core.clock.TickingClock;
import com.ntros.core.control.ChannelIntentTranslator;
import com.ntros.core.control.SwappableIntentTranslator;
import com.ntros.core.ecs.system.*;
import com.ntros.core.processor.WorldStateProcessor;
import com.ntros.core.updater.Actor;
import com.ntros.core.updater.StateActor;
import com.ntros.core.world.World;
import com.ntros.core.world.snapshot.WorldSnapshot;
import com.ntros.core.world.terrain.TerrainGenerationSettings;
import com.ntros.generator.GenerationWorker;
import com.ntros.graphics.rendering.AppGuiRunner;
import com.ntros.graphics.rendering.StateUIRenderer;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wires the GUI screens to the simulation runtime: WorldSetupPanel requests generation, generation
 * completion starts the sim and switches to the simulation screen.
 */
public final class AppGuiBootstrapper {
  // How much time the UI sits idle before rendering the next frame
  private static final int RENDERER_DELAY_MS = 16;
  // Default capacity for commands, drained by the processor thread
  private static final int COMMAND_QUEUE_CAPACITY = 1024;

  private static final Logger log = LoggerFactory.getLogger(AppGuiBootstrapper.class);

  // The command facade held by the GUI to send commands to the state-proc.
  // When a sim-run is terminated, and a new sim is requested -> swaps the translator instance with
  // a fresh one.
  private final SwappableIntentTranslator intentTranslator = new SwappableIntentTranslator();

  // Builds the Main Window and its components. Must be called on the EDT
  private AppGuiRunner appGuiRunner;
  // Orchestrates the execution state: state-proc thread, EDT Timer and the world-saver
  private SimulationController simulationController;

  public void bootstrapApplication(long seed) {
    // build AND show on the EDT: Swing components must be created and displayed there
    SwingUtilities.invokeLater(
        () -> {
          appGuiRunner =
              new AppGuiRunner(
                  AppConstants.MAIN_WINDOW_WIDTH,
                  AppConstants.MAIN_WINDOW_HEIGHT,
                  seed,
                  this::onGenerationRequested,
                  intentTranslator);
          appGuiRunner.showMainWindow();
        });
  }

  /** Stops the running simulation, if any. Safe to call from a shutdown hook. */
  public void shutdown() throws InterruptedException {
    if (simulationController != null) {
      simulationController.stop();
    }
  }

  // This is a callback that gets passed down multiple steps. Can get messy when multiple different
  // generations are needed.
  // TODO: explore different generation triggering options
  private void onGenerationRequested(TerrainGenerationSettings settings) {
    // run heavy tasks in independent threads, off the EDT
    // TODO: create multiple generations for different objects in the world
    GenerationWorker generationWorker = new GenerationWorker(settings);
    generationWorker.start();
    // on success: submit startSim to the EDT
    generationWorker
        .deliver()
        .thenAcceptAsync(this::startSimulation, SwingUtilities::invokeLater)
        .exceptionallyAsync( // on failure: unfold exception and supply to EDT
            ex -> {
              Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
              log.error("World generation failed", cause);
              appGuiRunner.getWorldSetupPanel().showGenerationError(cause);
              return null;
            },
            SwingUtilities::invokeLater);
  }

  // clears the current sim-state and builds a fresh one
  private void startSimulation(World world) {
    log.info("Sim start requested. Initializing state components..");
    stopCurrentSimulation();
    // fresh token per run, cannot reuse cancelled tokens
    CancellationToken token = new CancellationToken();
    // world state data [loop -> renderer]
    AtomicReference<WorldSnapshot> latestSnapshot = new AtomicReference<>();
    // Player commands [renderer -> loop]
    CommandChannel channel = new CommandChannel(COMMAND_QUEUE_CAPACITY);
    // measures wall time
    TickingClock clock = SimClock.ofDefaultTimeScale();

    // Create actor. Tick order and per-system RNG streams live in the factory so the ecosystem
    // regression test runs exactly this wiring.
    Actor actor = StateActor.ofEcosystem(world.getSeed());

    // The State Updater
    log.info("Initialising State Processor...");
    WorldStateProcessor worldStateProcessor =
        new WorldStateProcessor(world, clock, actor, channel, latestSnapshot, token);
    log.info("State Processor initialized.");
    // The UI Updater
    StateUIRenderer renderer = new StateUIRenderer(appGuiRunner.getWorldSimPanel(), latestSnapshot);
    // Handles the Simulation lifecycle
    // StatsChannel is created on the world. Analytics system writes to it, world-saver consumes it.
    simulationController =
        new SimulationController(
            worldStateProcessor, renderer, RENDERER_DELAY_MS, token, world.getStatsChannel());

    simulationController.start();
    log.info("Sim started");

    // sets a new intent transporter for a fresh run
    intentTranslator.setDelegate(new ChannelIntentTranslator(channel));
    appGuiRunner.getWorldSetupPanel().setGenerating(false);
    appGuiRunner.getScreenController().show(SIMULATION);
    log.info("Displaying world...");
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
