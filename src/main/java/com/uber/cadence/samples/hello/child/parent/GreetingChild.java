package com.uber.cadence.samples.hello.child.parent;

import com.uber.cadence.workflow.WorkflowMethod;

public interface GreetingChild {

  public static final String TASK_LIST_CHILD = "HelloChild";

  /** @return greeting string */
  @WorkflowMethod(taskList = TASK_LIST_CHILD)
  String composeGreeting(String greeting, String name);
}
