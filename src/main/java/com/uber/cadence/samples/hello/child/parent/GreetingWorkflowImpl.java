package com.uber.cadence.samples.hello.child.parent;

import com.uber.cadence.workflow.Async;
import com.uber.cadence.workflow.ChildWorkflowOptions;
import com.uber.cadence.workflow.Promise;
import com.uber.cadence.workflow.Workflow;

public class GreetingWorkflowImpl implements GreetingWorkflow {

  @Override
  public String getGreeting(String name) {
    //Define tasklist for child
   // ChildWorkflowOptions options = new ChildWorkflowOptions.Builder().setTaskList(TASK_LIST_CHILD).build();

    // Workflows are stateful. So a new stub must be created for each new child.
    //GreetingChild child = Workflow.newChildWorkflowStub(GreetingChild.class,  options);
    GreetingChild child = Workflow.newChildWorkflowStub(GreetingChild.class);

    // This is a blocking call that returns only after the child has completed.
    Promise<String> greeting = Async.function(child::composeGreeting, "Hello", name);
    // Do something else here.
    return greeting.get(); // blocks waiting for the child to complete.
  }
}
