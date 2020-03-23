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

package com.uber.cadence.samples.helloRetrySaga;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;
import static com.uber.cadence.samples.hello.child.parent.GreetingChild.TASK_LIST_CHILD;

import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.common.RetryOptions;
import com.uber.cadence.worker.Worker;
import java.time.Duration;
import java.util.UUID;

/**
 * Demonstrates activity retries using an exponential backoff algorithm. Requires a local instance
 * of the Cadence service to be running.
 */
public class HelloActivityRetry {

  public static final String TASK_LIST = "HelloActivityRetry";
  public static final String TASK_LIST_CHILD = "HelloChild";

  public static void main(String[] args) {
    // Start a worker that hosts both workflow and activity implementations.
    Worker.Factory factory = new Worker.Factory(DOMAIN);
    Worker worker = factory.newWorker(TASK_LIST);
    // Workflows are stateful. So you need a type to create instances.
    worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);

    Worker workerChild = factory.newWorker(TASK_LIST_CHILD);
    workerChild.registerWorkflowImplementationTypes(GreetingChildWorkflowImpl.class);
    // Activities are stateless and thread safe. So a shared instance is used.
    worker.registerActivitiesImplementations(new GreetingActivitiesImpl());
    // Start listening to the workflow and activity task lists.
    factory.start();

    // Start a workflow execution. Usually this is done from another program.
    WorkflowClient workflowClient = WorkflowClient.newInstance(DOMAIN);
    // Get a workflow stub using the same task list the worker uses.
    String workflowId = UUID.randomUUID().toString();
    WorkflowOptions workflowOptions =
        new WorkflowOptions.Builder()
            .setWorkflowId(workflowId)
            .setTaskList(TASK_LIST)
            .setExecutionStartToCloseTimeout(Duration.ofDays(1))
            .setRetryOptions(
                new RetryOptions.Builder()
                    .setExpiration(Duration.ofMinutes(100))
                    .setInitialInterval(Duration.ofSeconds(1))
                    .build())
            .build();
    GreetingWorkflow workflow =
        workflowClient.newWorkflowStub(GreetingWorkflow.class, workflowOptions);
    // Execute a workflow waiting for it to complete.
    WorkflowClient.start(workflow::getGreeting, "World", workflowId);
  }
}
