package com.uber.cadence.samples.helloRetrySaga;

import com.uber.cadence.workflow.WorkflowMethod;

public interface GreetingChildWorkflow {
  /** @return greeting string */
  @WorkflowMethod
  String getChildGreeting(String name, String workflowId);
}
