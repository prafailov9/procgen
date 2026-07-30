package com.ntros.core.processor;

public class ProcessorSimStats implements MutableSimStats {
  private long lastPublishTimeNanos;
  private long lastPublishedTick;
  private double elapsedRealTime;
  private double timeBucket;

  @Override
  public void setLastPublishTimeNanos(long t) {
    lastPublishTimeNanos = t;
  }

  @Override
  public void setLastPublishedTick(long t) {
    lastPublishedTick = t;
  }

  @Override
  public void setElapsedRealTime(double e) {
    elapsedRealTime = e;
  }

  @Override
  public void setTimeBucket(double timeBucket) {
    this.timeBucket = timeBucket;
  }

  @Override
  public void fillTimeBucket(double fill) {
    timeBucket += fill;
  }

  @Override
  public void drainTimeBucket(double drain) {
    timeBucket -= drain;
  }

  @Override
  public long getLastPublishTimeNanos() {
    return lastPublishTimeNanos;
  }

  @Override
  public long getLastPublishedTick() {
    return lastPublishedTick;
  }

  @Override
  public double getElapsedRealTime() {
    return elapsedRealTime;
  }

  @Override
  public double getTimeBucket() {
    return timeBucket;
  }

  @Override
  public String toString() {
    return "ProcessorSimStats{"
        + "lastPublishTimeNanos="
        + lastPublishTimeNanos
        + ", lastPublishedTick="
        + lastPublishedTick
        + ", elapsedTimeBetweenSpins="
        + elapsedRealTime
        + ", timeBucket="
        + timeBucket
        + '}';
  }
}
