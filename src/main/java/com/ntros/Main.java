package com.ntros;

import com.ntros.bootstrap.AppGuiBootstrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

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
                  } catch (InterruptedException e) {
                    log.error("Interruption while shutdown.", e);
                    Thread.currentThread().interrupt();
                  }
                }));
    log.info("Starting ProcGen...");
    bootstrapper.bootstrapApplication(seed);
  }
}
