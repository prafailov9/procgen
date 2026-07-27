package com.ntros;

import com.ntros.bootstrap.AppGuiBootstrapper;

public class Main {

  public static void main(String[] args) {
    AppGuiBootstrapper bootstrapper = new AppGuiBootstrapper();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    bootstrapper.shutdown();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                }));

    bootstrapper.bootstrapApplication();
  }
}
