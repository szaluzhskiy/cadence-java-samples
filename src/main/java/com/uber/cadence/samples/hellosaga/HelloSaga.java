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

package com.uber.cadence.samples.hellosaga;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.samples.hellosaga.saga.CompensationWorkflowImpl;
import com.uber.cadence.samples.hellosaga.saga.Saga;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.workflow.*;

import java.io.IOException;

public class HelloSaga {
  static final String TASK_LIST = "HelloSaga";

  /** The parent workflow interface. */
  public interface GreetingWorkflow {
    /** @return greeting string */
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 10, taskList = TASK_LIST)
    String getGreeting(String name);
  }

  public interface GreetingActivities {
    @ActivityMethod(scheduleToCloseTimeoutSeconds = 10)
    Saga.Result<String, String> makeGreeting(String name);

    @ActivityMethod(scheduleToCloseTimeoutSeconds = 10)
    void makeGreetingCompensation(String name);

    @ActivityMethod(scheduleToCloseTimeoutSeconds = 10)
    void failGreeting(String name);
  }

  /** The child workflow interface. */
  public interface GreetingChild {
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 10, taskList = TASK_LIST)
    Saga.WorkflowResult<String> composeGreeting(String name);
  }

  public interface GreetingChildActivities {
    @ActivityMethod(scheduleToCloseTimeoutSeconds = 10)
    Saga.Result<String, String> makeGreeting(String name);

    @ActivityMethod(scheduleToCloseTimeoutSeconds = 10)
    void makeGreetingCompensation(String name);
  }

  /** GreetingWorkflow implementation that calls GreetingsActivities#printIt. */
  public static class GreetingWorkflowImpl implements GreetingWorkflow {

    private final Saga saga = new Saga();
    private final GreetingActivities activities =
        Workflow.newActivityStub(GreetingActivities.class);
    private final GreetingChild child = Workflow.newChildWorkflowStub(GreetingChild.class);

    @Override
    public String getGreeting(String name) {

      try {
        // Executing an activity with compensation
        // Sync API
        String parentGreeting =
            saga.executeFunc(activities::makeGreeting, name, activities::makeGreetingCompensation);

        // Executing a child workflow with compensation
        String childGreeting = saga.executeChildFuncAsync(child::composeGreeting, name).get();

        // Fail at the very end of processing
        activities.failGreeting(name);

        return parentGreeting + " and " + childGreeting;
      } catch (ActivityFailureException ex) {
        System.out.println(ex.getMessage());
        // In case of error in parent workflow - compensate everything
        saga.compensate();
        return "Epic fail :(";
      }
    }
  }

  public static class GreetingActivitiesImpl implements GreetingActivities {

    @Override
    public Saga.Result<String, String> makeGreeting(String name) {
      System.out.println("GreetingActivitiesImpl::makeGreeting for " + name);
      return new Saga.Result<>("Hello " + name, name);
    }

    @Override
    public void makeGreetingCompensation(String name) {
      System.out.println("GreetingActivitiesImpl::makeGreetingCompensation for " + name);
    }

    @Override
    public void failGreeting(String name)  {
      System.out.println("GreetingActivitiesImpl::failGreeting");
      try {
        throw new IOException("Something bad happened");
      } catch (IOException e) {
        throw Workflow.wrap(e);
      }
    }
  }

  /**
   * The child workflow implementation. A workflow implementation must always be public for the
   * Cadence library to be able to create instances.
   */
  public static class GreetingChildImpl implements GreetingChild {

    private final Saga saga = new Saga();
    private final GreetingChildActivities activities =
        Workflow.newActivityStub(GreetingChildActivities.class);
    private final GreetingChild child = Workflow.newChildWorkflowStub(GreetingChild.class);

    @Override
    public Saga.WorkflowResult<String> composeGreeting(String name) {
      try {
        // Execute an action with compensation
        String greeting =
            saga.executeFunc(activities::makeGreeting, name + " 0", activities::makeGreetingCompensation);
        // Async API opt.1
        String greeting1 = saga
            .executeFuncAsync(activities::makeGreeting, name + " 1", activities::makeGreetingCompensation)
            .get();
        // Async API opt.2
        String greeting2 = Async
            .function(activities::makeGreeting, name + " 2")
            .thenApply(r -> saga.withCompensation(r, activities::makeGreetingCompensation))
            .get();
        // Async API opt.3
        String greeting3 = Async
            .function(activities::makeGreeting, name + " 3")
            .thenApply(saga.withCompensation(activities::makeGreetingCompensation))
            .get();

        // Add recursive call to check deeply nested compensation works.
        if (!name.startsWith("____")) {
           saga.executeChildFuncAsync(child::composeGreeting, "_" + name).get();
        }

        // Return all the data required for compensation of the whole workflow
        return new Saga.WorkflowResult<>(greeting, saga.exportCompensations());
      } catch (ActivityFailureException ex) {
        // In case of error in a child workflow - compensate own actions and rethrow
        saga.compensate();
        throw ex;
      }
    }
  }

  public static class GreetingChildActivitiesImpl implements GreetingChildActivities {
    @Override
    public Saga.Result<String, String> makeGreeting(String name) {
      System.out.println("GreetingChildActivitiesImpl::makeGreeting for " + name);
      return new Saga.Result<>("Hello from child " + name, name);
    }

    @Override
    public void makeGreetingCompensation(String name) {
      System.out.println("GreetingChildActivitiesImpl::makeGreetingCompensation for " + name);
    }
  }

  public static void main(String[] args) {
    // Start a worker that hosts both parent and child workflow implementations.
    Worker.Factory factory = new Worker.Factory(DOMAIN);
    Worker worker = factory.newWorker(TASK_LIST);
    worker.registerWorkflowImplementationTypes(
        GreetingWorkflowImpl.class,
        GreetingChildImpl.class,
        // register a standard compensation workflow
        CompensationWorkflowImpl.class);
    worker.registerActivitiesImplementations(
        new GreetingActivitiesImpl(), new GreetingChildActivitiesImpl());
    // Start listening to the workflow task list.
    factory.start();

    // Start a workflow execution. Usually this is done from another program.
    WorkflowClient workflowClient = WorkflowClient.newInstance(DOMAIN);
    // Get a workflow stub using the same task list the worker uses.
    GreetingWorkflow workflow = workflowClient.newWorkflowStub(GreetingWorkflow.class);
    // Execute a workflow waiting for it to complete.
    String greeting = workflow.getGreeting("World");
    System.out.println(greeting);
    System.exit(0);
  }
}
