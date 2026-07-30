package com.ntros.core.processor;

public interface SimStats {
  long getLastPublishTimeNanos();

  long getLastPublishedTick();

  double getElapsedRealTime();

  double getTimeBucket();
}
