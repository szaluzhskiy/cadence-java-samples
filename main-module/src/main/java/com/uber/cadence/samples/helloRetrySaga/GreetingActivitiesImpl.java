package com.uber.cadence.samples.helloRetrySaga;

import java.util.concurrent.atomic.AtomicInteger;

class GreetingActivitiesImpl implements GreetingActivities {
  private int callCount;
  private long lastInvocationTime;

  @Override
  public String composeGreeting(String workflowId, AtomicInteger activityCounter) {
    System.out.println("Attempt - " + activityCounter.incrementAndGet() + " wid - " + workflowId);
    throw new RuntimeException();
  }
}
