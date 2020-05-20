package com.uber.cadence.samples.helloRetrySaga;

import com.uber.cadence.workflow.WorkflowMethod;

public interface GreetingWorkflow {
  /** @return greeting string */
  @WorkflowMethod
  String getGreeting(String name, String workflowId);
}
