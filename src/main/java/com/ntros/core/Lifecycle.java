package com.ntros.core;

public interface Lifecycle {
    void start();
    void stop() throws InterruptedException;
}
