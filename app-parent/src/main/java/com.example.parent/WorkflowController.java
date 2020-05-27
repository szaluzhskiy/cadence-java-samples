package com.example.parent;

import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.client.WorkflowClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;

@RestController
public class WorkflowController {

  WorkflowClient workflowClient;

  @PostConstruct
  private void init() {
    WorkflowClient workflowClient = WorkflowClient.newInstance(
        "127.0.0.1",
        7933,
        DOMAIN);
  }

  @GetMapping("/start/wf")
  public void startWorkflow() {
    // Start a workflow execution. Usually this is done from another program.
    ParentWorkflow.GreetingWorkflow parentWorkflow;

    parentWorkflow = workflowClient.newWorkflowStub(ParentWorkflow.GreetingWorkflow.class);
    WorkflowExecution we = WorkflowClient.start(parentWorkflow::getGreeting, "World");
  }
}
