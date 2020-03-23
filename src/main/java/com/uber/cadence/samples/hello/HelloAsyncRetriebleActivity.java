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

import com.uber.cadence.activity.Activity;
import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.client.ActivityCompletionClient;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.common.RetryOptions;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.workflow.Async;
import com.uber.cadence.workflow.Promise;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

/**
 * Demonstrates an asynchronous activity implementation. Requires a local instance of Cadence server
 * to be running.
 */
public class HelloAsyncRetriebleActivity {

  static final String TASK_LIST = "HelloAsyncActivityCompletion";

  public interface GreetingWorkflow {
    /** @return greeting string */
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 15, taskList = TASK_LIST)
    String getGreeting(String name);
  }

  /** Activity interface is just a POJI. * */
  public interface GreetingActivities {
    @ActivityMethod(scheduleToCloseTimeoutSeconds = 60)
    String composeGreeting(String greeting, String name);
  }

  /** Activity interface is just a POJI. * */
  public interface AngryActivities {
    @ActivityMethod(scheduleToCloseTimeoutSeconds = 60)
    String composeAngryGreeting(String greeting, String name);
  }

  /** GreetingWorkflow implementation that calls GreetingsActivities#printIt. */
  public static class GreetingWorkflowImpl implements GreetingWorkflow {

    /**
     * Activity stub implements activity interface and proxies calls to it to Cadence activity
     * invocations. Because activities are reentrant, only a single stub can be used for multiple
     * activity invocations.
     */
    private final GreetingActivities activities =
        Workflow.newActivityStub(GreetingActivities.class);

    private final AngryActivities angryActivities = Workflow.newActivityStub(AngryActivities.class);

    @Override
    public String getGreeting(String name) {
      // This is a non-blocking call that returns immediatly after the activity has completed.
      Promise<String> positiveActivity =
          Async.retry(
              GreetingActivitiesImpl.getActivityRetryOptions().get(),
              () -> Async.function(activities::composeGreeting, "Hello", name));

      positiveActivity.get();
      // This is a non-blocking call which will throw Runtime exception.
      Promise<String> negativeActivity =
          Async.retry(
              GreetingActivitiesImpl.getActivityRetryOptions().get(),
              () -> Async.function(angryActivities::composeAngryGreeting, "Hello", name));

      negativeActivity.get();
      return "Started";
    }
  }

  static class GreetingActivitiesImpl implements GreetingActivities {
    private final ActivityCompletionClient completionClient;

    GreetingActivitiesImpl(ActivityCompletionClient completionClient) {
      this.completionClient = completionClient;
    }

    /**
     * Demonstrates how to implement an activity asynchronously. When {@link
     * Activity#doNotCompleteOnReturn()} is called the activity implementation function returning
     * doesn't complete the activity.
     */
    @Override
    public String composeGreeting(String greeting, String name) {
      // In real life this request can be executed anywhere. By a separate service for
      // example.
      ForkJoinPool.commonPool().execute(() -> composeGreetingAsync(greeting, name));

      // When doNotCompleteOnReturn() is invoked the return value is ignored.
      return "ignored";
    }

    private void composeGreetingAsync(String greeting, String name) {
      String result = greeting + " " + name + "!";
      System.out.println("Activity completed");
    }

    public static Optional<RetryOptions> getActivityRetryOptions() {
      return Optional.of(
          new RetryOptions.Builder()
              .setDoNotRetry(RuntimeException.class)
              .setMaximumAttempts(1000)
              .setInitialInterval(Duration.ofSeconds(1))
              .setBackoffCoefficient(2.0)
              .build());
    }
  }

  static class AngryActivitiesImpl implements AngryActivities {

    @Override
    public String composeAngryGreeting(String greeting, String name) {
      throw new RuntimeException("This is the end!");
      // return "angry activity";
    }

    public static Optional<RetryOptions> getActivityRetryOptions() {
      return Optional.of(
          new RetryOptions.Builder()
              .setDoNotRetry(RuntimeException.class)
              .setMaximumAttempts(1000)
              .setInitialInterval(Duration.ofSeconds(1))
              .setBackoffCoefficient(2.0)
              .build());
    }
  }

  public static void main(String[] args) throws ExecutionException, InterruptedException {
    // Start a workflow execution. Usually this is done from another program.
    WorkflowClient workflowClient = WorkflowClient.newInstance(DOMAIN);

    // Start a worker that hosts both workflow and activity implementations.
    Worker.Factory factory = new Worker.Factory(DOMAIN);
    Worker worker = factory.newWorker(TASK_LIST);
    // Workflows are stateful. So you need a type to create instances.
    worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
    // Activities are stateless and thread safe. So a shared instance is used.
    // CompletionClient is passed to activity here only to support unit testing.
    ActivityCompletionClient completionClient = workflowClient.newActivityCompletionClient();
    worker.registerActivitiesImplementations(
        new GreetingActivitiesImpl(completionClient), new AngryActivitiesImpl());
    // Start listening to the workflow and activity task lists.
    factory.start();

    // Get a workflow stub using the same task list the worker uses.
    GreetingWorkflow workflow = workflowClient.newWorkflowStub(GreetingWorkflow.class);
    // Execute a workflow returning a future that can be used to wait for the workflow
    // completion.
    CompletableFuture<String> greeting = WorkflowClient.execute(workflow::getGreeting, "World");
    // Wait for workflow completion.
    System.out.println(greeting.get());
    System.exit(0);
  }
}
