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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.converter.JsonDataConverter;
import com.uber.cadence.samples.hello.extandabletypeadapter.ExtendableTypeAdapterFactory;
import com.uber.cadence.samples.hello.extandabletypeadapter.GreetingBaseArgs;
import com.uber.cadence.samples.hello.extandabletypeadapter.GreetingBaseArgsExtended;
import com.uber.cadence.samples.hello.extandabletypeadapter.GreetingChildArgs;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerOptions;
import com.uber.cadence.workflow.ChildWorkflowStub;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import java.io.IOException;

/** Demonstrates a child workflow. Requires a local instance of the Cadence server to be running. */
public class HelloChildWithInheritedArgsFull {

  static final String TASK_LIST = "HelloChild";

  /** The parent workflow interface. */
  public interface GreetingWorkflow {
    /** @return greeting string */
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 10, taskList = TASK_LIST)
    String getGreeting(GreetingBaseArgsExtended args);
  }

  /** The child workflow interface. */
  public interface GreetingChild {
    @WorkflowMethod
    String composeGreeting(String greeting, GreetingChildArgs args);
  }

  /** GreetingWorkflow implementation that calls GreetingsActivities#printIt. */
  public static class GreetingWorkflowImpl implements GreetingWorkflow {

    @Override
    public String getGreeting(GreetingBaseArgsExtended args) {
      // Workflows are stateful. So a new stub must be created for each new child.
      ChildWorkflowStub child = Workflow.newUntypedChildWorkflowStub("GreetingChild::composeGreeting");

      return child.execute(String.class, "Hello", args);
    }
  }

  /**
   * The child workflow implementation. A workflow implementation must always be public for the
   * Cadence library to be able to create instances.
   */
  public static class GreetingChildImpl implements GreetingChild {
    @Override
    public String composeGreeting(String greeting, GreetingChildArgs args) {
      return greeting + " " + args.getName() + " - " + args.getOtherName() + "!";
    }
  }

  public static void main(String[] args) throws IOException {
    ObjectMapper mapper = new ObjectMapper();

    GreetingChildArgs child = new GreetingChildArgs("parent", "child");

    String source = mapper.writeValueAsString(child);

    GreetingBaseArgsExtended extended = mapper.readValue(source.getBytes(), GreetingBaseArgsExtended.class);

    // Start a worker that hosts both parent and child workflow implementations.
    Worker.Factory factory = new Worker.Factory(DOMAIN);
    Worker worker = factory.newWorker(
        TASK_LIST,
        new WorkerOptions.Builder()
            .setDataConverter(new JsonDataConverter(builder ->
                builder.registerTypeAdapterFactory(
                    new ExtendableTypeAdapterFactory(GreetingBaseArgsExtended.class, GreetingBaseArgsExtended.class))
            ))
            .build());
    worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class, GreetingChildImpl.class);
    // Start listening to the workflow task list.
    factory.start();

    // Start a workflow execution. Usually this is done from another program.
    WorkflowClient workflowClient = WorkflowClient.newInstance("127.0.0.1", 7933, DOMAIN);
    // Get a workflow stub using the same task list the worker uses.
    GreetingWorkflow workflow = workflowClient.newWorkflowStub(GreetingWorkflow.class);

    // Execute a workflow waiting for it to complete.
    String greeting = workflow.getGreeting(extended);
    System.out.println(greeting);
    System.exit(0);
  }
}
