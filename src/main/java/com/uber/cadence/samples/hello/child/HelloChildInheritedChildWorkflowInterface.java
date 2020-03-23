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

package com.uber.cadence.samples.hello.child;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;
import static com.uber.cadence.samples.hello.child.parent.GreetingChild.TASK_LIST_CHILD;
import static com.uber.cadence.samples.hello.child.parent.GreetingWorkflow.TASK_LIST_PARENT;

import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.samples.hello.HelloChild.GreetingWorkflow;
import com.uber.cadence.samples.hello.child.child.GreetingChildSubInterfaceImpl;
import com.uber.cadence.samples.hello.child.parent.GreetingWorkflowImpl;
import com.uber.cadence.worker.Worker;

/** Demonstrates a child workflow. Requires a local instance of the Cadence server to be running. */
public class HelloChildInheritedChildWorkflowInterface {

  public static void main(String[] args) {
    // Start a worker that hosts both parent and child workflow implementations.
    Worker.Factory factory = new Worker.Factory(DOMAIN);
    Worker workerParent = factory.newWorker(TASK_LIST_PARENT);
    workerParent.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);

    Worker workerChild = factory.newWorker(TASK_LIST_CHILD);
    workerChild.registerWorkflowImplementationTypes(GreetingChildSubInterfaceImpl.class);

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
