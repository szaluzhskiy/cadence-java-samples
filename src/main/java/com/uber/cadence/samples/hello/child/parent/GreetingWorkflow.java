package com.uber.cadence.samples.hello.child.parent;

import com.uber.cadence.workflow.WorkflowMethod;

/** The parent workflow interface. */
public interface GreetingWorkflow {
  public static final String TASK_LIST_PARENT = "HelloParent";

  /** @return greeting string */
  @WorkflowMethod(executionStartToCloseTimeoutSeconds = 10, taskList = TASK_LIST_PARENT)
  String getGreeting(String name);
}
