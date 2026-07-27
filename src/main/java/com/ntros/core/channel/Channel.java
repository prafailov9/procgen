package com.ntros.core.channel;

public interface Channel {

    boolean tryOffer(Command command);

    void forceOffer(Command command);

    /**
     * @return the next command, or null when the channel is empty. Never blocks — the sim loop
     *     drains pending commands and moves on to ticking.
     */
    Command poll();
}
