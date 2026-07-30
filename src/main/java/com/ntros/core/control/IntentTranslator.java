package com.ntros.core.control;

import com.ntros.core.SimulationSpeed;

/** Contract for sending intents to underlying listeners. Decouples sender from transport */
public interface IntentTranslator {

  void changeSpeed(SimulationSpeed speed);
}
