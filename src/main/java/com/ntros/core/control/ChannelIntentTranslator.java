package com.ntros.core.control;

import com.ntros.core.SimulationSpeed;
import com.ntros.core.channel.Channel;
import com.ntros.core.command.ChangeSpeedCommand;

/** Translates intents into commands on a running simulation's channel. */
public final class ChannelIntentTranslator implements IntentTranslator {

  private final Channel channel;

  public ChannelIntentTranslator(Channel channel) {
    this.channel = channel;
  }

  @Override
  public void changeSpeed(SimulationSpeed speed) {
    channel.tryOffer(ChangeSpeedCommand.of(speed));
  }
}
