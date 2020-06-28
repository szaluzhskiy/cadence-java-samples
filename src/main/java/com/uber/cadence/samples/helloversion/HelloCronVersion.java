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

package com.uber.cadence.samples.helloversion;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;

/**
 * Hello World Cadence workflow that executes a single activity. Requires a local instance the
 * Cadence service to be running.
 */
public class HelloCronVersion {

  static final String TASK_LIST = "HelloCronVersion";

  private static String cronScheduler = "* * * * *";

  /** Workflow interface has to have at least one method annotated with @WorkflowMethod. */
  public interface GreetingWorkflow {
    /** @return greeting string */
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 1200, taskList = TASK_LIST)
    String getGreeting(String name);
  }

  /** Activity interface is just a POJO. */
  public interface GreetingActivities {
    // STEP 1
    @ActivityMethod(scheduleToCloseTimeoutSeconds = 200)
    String composeGreeting(String greeting, String name);

    // STEP 2. Uncomment this
    @ActivityMethod(scheduleToCloseTimeoutSeconds = 200)
    String newComposeGreeting(String greeting, String name);
  }

  /** GreetingWorkflow implementation that calls GreetingsActivities#composeGreeting. */
  public static class GreetingWorkflowImpl implements GreetingWorkflow {

    /**
     * Activity stub implements activity interface and proxies calls to it to Cadence activity
     * invocations. Because activities are reentrant, only a single stub can be used for multiple
     * activity invocations.
     */
    private final GreetingActivities activities =
        Workflow.newActivityStub(GreetingActivities.class);

    @Override
    public String getGreeting(String name) {
      //// STEP 2. Uncomment
      int version =
          Workflow.getVersion("newGenerationComposeGreeting", Workflow.DEFAULT_VERSION, 1);
      if (version == Workflow.DEFAULT_VERSION) {
        System.out.println("oldGenerationComposeGreeting");
        return activities.composeGreeting("Hello", name);
        // STEP 2. Uncomment
      } else {
        System.out.println("newGenerationComposeGreeting");
        return activities.newComposeGreeting("Hello new compose activity ", name);
      }
    }
  }

  static class GreetingActivitiesImpl implements GreetingActivities {
    @Override
    public String composeGreeting(String greeting, String name) {
      System.out.println("sleep composeGreeting");
      try {
        Thread.sleep(50 * 1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      System.out.println("wake up composeGreeting");
      return greeting + " " + name + "!";
    }

    // STEP 2. Uncomment
    @Override
    public String newComposeGreeting(String greeting, String name) {
      return greeting + " " + name + "!";
    }
  }

  public static void main(String[] args) {
    // Start a worker that hosts both workflow and activity implementations.
    Worker.Factory factory = new Worker.Factory(DOMAIN);
    Worker worker = factory.newWorker(TASK_LIST);
    // Workflows are stateful. So you need a type to create instances.
    worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
    // Activities are stateless and thread safe. So a shared instance is used.
    worker.registerActivitiesImplementations(new GreetingActivitiesImpl());
    // Start listening to the workflow and activity task lists.
    factory.start();

    // Start a workflow execution. Usually this is done from another program.
    WorkflowClient workflowClient = WorkflowClient.newInstance(DOMAIN);
    // Get a workflow stub using the same task list the worker uses.
    WorkflowOptions wo =
        new WorkflowOptions.Builder().setCronSchedule(cronScheduler).setTaskList(TASK_LIST).build();

    GreetingWorkflow workflow = workflowClient.newWorkflowStub(GreetingWorkflow.class, wo);
    // Execute a workflow waiting for it to complete.
    String greeting = workflow.getGreeting("World");
    System.out.println(greeting);
    System.exit(0);
  }
}
