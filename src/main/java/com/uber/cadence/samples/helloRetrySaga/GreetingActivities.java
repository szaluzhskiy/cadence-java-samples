package com.uber.cadence.samples.helloRetrySaga;

import com.uber.cadence.activity.ActivityMethod;
import java.util.concurrent.atomic.AtomicInteger;

public interface GreetingActivities {
  @ActivityMethod
  String composeGreeting(String workflowId, AtomicInteger activityCounter);
}
