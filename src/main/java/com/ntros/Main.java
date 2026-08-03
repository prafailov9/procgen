package com.ntros;

import com.ntros.bootstrap.AppGuiBootstrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * Main Entrypoint. Configures the Main Window and panels. On Sim-Start - main thread hands off
 * control to the Swing EDT and exists. The EDT drives the entire application onwards.
 */
public class Main {
  private static final Logger log = LoggerFactory.getLogger(Main.class);

  static void main() {
    long seed = new Random().nextLong();
    log.info("Generated seed: {}", seed);

    AppGuiBootstrapper bootstrapper = new AppGuiBootstrapper();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    bootstrapper.shutdown();
                  } catch (InterruptedException ex) {
                    log.error("Interruption while shutdown.", ex);
                    Thread.currentThread().interrupt();
                  }
                }));
    log.info("Starting ProcGen...");
    bootstrapper.bootstrapApplication(seed);
  }
}
