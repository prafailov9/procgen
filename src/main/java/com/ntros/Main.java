package com.ntros;

import com.ntros.bootstrap.AppGuiBootstrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

public class Main {
    public static final long SEED = ThreadLocalRandom.current().nextLong(1, 100);
  private static final Logger log = LoggerFactory.getLogger(Main.class);

  static void main() {
      log.info("Generated seed: {}", SEED);

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
    bootstrapper.bootstrapApplication();
  }
}
