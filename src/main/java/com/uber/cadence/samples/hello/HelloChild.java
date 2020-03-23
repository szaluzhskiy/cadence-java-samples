/*
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package com.uber.cadence.samples.hello;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.workflow.Async;
import com.uber.cadence.workflow.ChildWorkflowOptions;
import com.uber.cadence.workflow.Promise;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;

/** Demonstrates a child workflow. Requires a local instance of the Cadence server to be running. */
public class HelloChild {

  static final String TASK_LIST_PARENT = "HelloParent";

  static final String TASK_LIST_CHILD = "HelloChild";

  /** The parent workflow interface. */
  public interface GreetingWorkflow {
    /** @return greeting string */
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 10, taskList = TASK_LIST_PARENT)
    String getGreeting(String name);
  }

  /** The child workflow interface. */
  public interface GreetingChild {
    @WorkflowMethod
    String composeGreeting(String greeting, String name);
  }

  /** The child workflow interface. */
  public interface GreetingChildOnParentSide extends GreetingChild {
    /** @return greeting string */
    @WorkflowMethod
    String composeGreeting(String greeting, String name);
  }

  /** The child workflow interface. */
  public interface GreetingChildOnChildSide extends GreetingChild {
    /** @return greeting string */
    @WorkflowMethod
    String composeGreeting(String greeting, String name);
  }

  /** GreetingWorkflow implementation that calls GreetingsActivities#printIt. */
  public static class GreetingWorkflowImpl implements GreetingWorkflow {

    @Override
    public String getGreeting(String name) {
      // Define tasklist for child
      ChildWorkflowOptions options =
          new ChildWorkflowOptions.Builder().setTaskList(TASK_LIST_CHILD).build();

      // Workflows are stateful. So a new stub must be created for each new child.
      GreetingChild child = Workflow.newChildWorkflowStub(GreetingChildOnParentSide.class, options);

      // This is a blocking call that returns only after the child has completed.
      Promise<String> greeting = Async.function(child::composeGreeting, "Hello", name);
      // Do something else here.
      return greeting.get(); // blocks waiting for the child to complete.
    }
  }

  /**
   * The child workflow implementation. A workflow implementation must always be public for the
   * Cadence library to be able to create instances.
   */
  public static class GreetingChildOnChildSideImpl implements GreetingChildOnChildSide {
    public String composeGreeting(String greeting, String name) {
      return greeting + " " + name + "!";
    }
  }

  public static void main(String[] args) {
    // Start a worker that hosts both parent and child workflow implementations.
    Worker.Factory factory = new Worker.Factory(DOMAIN);
    Worker workerParent = factory.newWorker(TASK_LIST_PARENT);
    workerParent.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);

    Worker workerChild = factory.newWorker(TASK_LIST_CHILD);
    workerChild.registerWorkflowImplementationTypes(GreetingChildOnChildSideImpl.class);

    // Start listening to the workflow and activity task lists.
    factory.start();

    // Start a workflow execution. Usually this is done from another program.
    WorkflowClient workflowClient = WorkflowClient.newInstance("127.0.0.1", 7933, DOMAIN);
    // Get a workflow stub using the same task list the worker uses.
    GreetingWorkflow parentWorkflow = workflowClient.newWorkflowStub(GreetingWorkflow.class);

    // Execute a workflow waiting for it to complete.
    String greeting = parentWorkflow.getGreeting("World");
    System.out.println(greeting);
    System.exit(0);
  }
}
