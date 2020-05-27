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

package com.example.parent;

import com.uber.cadence.DomainAlreadyExistsError;
import com.uber.cadence.RegisterDomainRequest;
import com.uber.cadence.activity.Activity;
import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.activity.ActivityOptions;
import com.uber.cadence.common.RetryOptions;
import com.uber.cadence.serviceclient.IWorkflowService;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerOptions;
import com.uber.cadence.workflow.*;
import com.uber.m3.tally.RootScopeBuilder;
import com.uber.m3.tally.Scope;
import com.uber.m3.util.Duration;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

import static com.example.parent.SampleConstants.*;

/** Demonstrates a child workflow. Requires a local instance of the Cadence server to be running. */
@Component
@Slf4j
public class ParentWorkflow {

  @PostConstruct
  public void init() {
    log.info("Run parent app");
    //registerDomain();
    startFactory();
  }

  private void startFactory() {
    // Start a worker that hosts both parent and child workflow implementations.
    Scope scope =
        new RootScopeBuilder()
            .reporter(new CustomCadenceClientStatsReporter())
            .reportEvery(Duration.ofSeconds(1));

    Worker.Factory factory =
        new Worker.Factory(
            System.getenv(CADENCE_HOST),
            Integer.parseInt(System.getenv(CADENCE_PORT)),
            DOMAIN,
            new Worker.FactoryOptions.Builder().setMetricScope(scope).build()
        );

    Worker workerParent =
        factory.newWorker(
            TASK_LIST_PARENT, new WorkerOptions.Builder().setMetricsScope(scope).build()
        );

    workerParent.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
    workerParent.registerActivitiesImplementations(new ParentActivitiesImpl());

    // Start listening to the workflow and activity task lists.
    factory.start();
    log.info("Started factory");
  }

  private void registerDomain() {
    IWorkflowService cadenceService = new WorkflowServiceTChannel(
            System.getenv(CADENCE_HOST),
            Integer.parseInt(System.getenv(CADENCE_PORT))
    );
    RegisterDomainRequest request = new RegisterDomainRequest();
    request.setDescription("Java Samples");
    request.setEmitMetric(false);
    request.setName(DOMAIN);
    int retentionPeriodInDays = 1;
    request.setWorkflowExecutionRetentionPeriodInDays(retentionPeriodInDays);
    try {
      cadenceService.RegisterDomain(request);
      System.out.println(
          "Successfully registered domain \""
              + DOMAIN
              + "\" with retentionDays="
              + retentionPeriodInDays);

    } catch (DomainAlreadyExistsError e) {
      log.error("Domain \"" + DOMAIN + "\" is already registered");

    } catch (TException e) {
      log.error("Error occurred", e);

    } finally {
      cadenceService.close();
    }
  }

  /** The parent workflow interface. */
  public interface GreetingWorkflow {
    /** @return greeting string */
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 60000, taskList = TASK_LIST_PARENT)
    String getGreeting(String name);
  }

//  /** The child workflow interface. */
//  public interface GreetingChild {
//    /** @return greeting string */
//    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 60000, taskList = TASK_LIST_CHILD)
//    String composeGreeting(String greeting, String name);
//  }

  public interface ParentActivities {
    @ActivityMethod(scheduleToStartTimeoutSeconds = 60000, startToCloseTimeoutSeconds = 60)
    String composeParentGreeting(int activityIdx);
  }

  static class ParentActivitiesImpl implements ParentActivities {
    @Override
    public String composeParentGreeting(int activityIdx) {
      return String.format("Finished parent activity: idx: [%d], activity id: [%s], task: [%s]", activityIdx,
              Activity.getTask().getActivityId(), new String(Activity.getTaskToken()));
    }
  }

  /** GreetingWorkflow implementation that calls GreetingsActivities#printIt. */
  public static class GreetingWorkflowImpl implements GreetingWorkflow {

    @Override
    public String getGreeting(String name) {
      // Define tasklist for child
      ChildWorkflowOptions options =
          new ChildWorkflowOptions.Builder().setTaskList(TASK_LIST_CHILD).build();

//      // Workflows are stateful. So a new stub must be created for each new child.
//      GreetingChild child = Workflow.newChildWorkflowStub(GreetingChild.class, options);
//
//      // This is a blocking call that returns only after the child has completed.
//      Promise<String> greeting = Async.function(child::composeGreeting, "Hello", name);
//      // blocks waiting for the child to complete.
//      String childResult = greeting.get();

      // Do something else here.
      Promise<List<String>> parentPromises = runParentActivities();
      List<String> promisesResults = parentPromises.get();
      System.out.println("Got result in parent: \n" + String.join(";\n", promisesResults) + "\n");

      return "EXIT FROM MAIN WF";
    }

    private Promise<List<String>> runParentActivities() {
      RetryOptions ro =
              new RetryOptions.Builder()
                      .setInitialInterval(java.time.Duration.ofSeconds(30))
                      .setMaximumInterval(java.time.Duration.ofSeconds(30))
                      .setMaximumAttempts(2)
                      .build();
      ActivityOptions ao =
              new ActivityOptions.Builder()
                      .setTaskList(TASK_LIST_PARENT)
                      .setRetryOptions(ro)
                      .build();

      List<Promise<String>> parentActivities = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
        ParentActivities activity = Workflow.newActivityStub(ParentActivities.class, ao);
        parentActivities.add(Async.function(activity::composeParentGreeting, i));
      }

      return Promise.allOf(parentActivities);
    }
  }
}