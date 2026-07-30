package com.ntros.core.control;

import com.ntros.core.SimulationSpeed;

/**
 * Stable controls handle for the UI. The GUI is built once, but the channel is born fresh with
 * each simulation run — this facade bridges that lifecycle gap: the bootstrapper swaps the
 * delegate in when a run starts and out when it stops. No delegate means no sim is running and
 * input is ignored.
 */
public final class SwappableIntentTranslator implements IntentTranslator {

  private volatile IntentTranslator delegate;

  public void setDelegate(IntentTranslator delegate) {
    this.delegate = delegate;
  }

  @Override
  public void changeSpeed(SimulationSpeed speed) {
    IntentTranslator current = delegate;
    if (current != null) {
      current.changeSpeed(speed);
    }
  }
}
