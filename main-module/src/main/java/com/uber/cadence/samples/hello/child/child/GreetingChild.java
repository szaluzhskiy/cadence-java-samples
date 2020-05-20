package com.uber.cadence.samples.hello.child.child;

import com.uber.cadence.workflow.WorkflowMethod;

public interface GreetingChild {
  /** @return greeting string */
  @WorkflowMethod
  String composeGreeting(String greeting, String name);
}
