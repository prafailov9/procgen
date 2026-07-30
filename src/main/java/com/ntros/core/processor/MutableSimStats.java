package com.ntros.core.processor;

public interface MutableSimStats extends SimStats {
  void setLastPublishTimeNanos(long t);

  void setLastPublishedTick(long t);

  void setElapsedRealTime(double e);

  void setTimeBucket(double timeBucket);

  void fillTimeBucket(double fill);

  void drainTimeBucket(double drain);
}
