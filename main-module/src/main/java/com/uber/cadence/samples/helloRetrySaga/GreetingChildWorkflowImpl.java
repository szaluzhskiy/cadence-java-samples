package com.uber.cadence.samples.helloRetrySaga;

public class GreetingChildWorkflowImpl implements GreetingChildWorkflow {
  /** @return greeting string */
  @Override
  public String getChildGreeting(String name, String workflowId) {
    System.out.println("GreetingChildWorkflowImpl");
    return "DONE";
    // throw new RuntimeException();
  }
}
