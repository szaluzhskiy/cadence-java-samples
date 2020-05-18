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
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.reporter.CadenceClientStatsReporter;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerOptions;
import com.uber.cadence.workflow.*;
import com.uber.m3.tally.RootScopeBuilder;
import com.uber.m3.tally.Scope;
import com.uber.m3.util.Duration;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.apache.commons.codec.binary.Hex;

/** Demonstrates a child workflow. Requires a local instance of the Cadence server to be running. */
public class HelloChildWithExternalRest {

  static final String TASK_LIST_PARENT = "HelloParent";

  static final String TASK_LIST_CHILD = "HelloChild";

  /** The parent workflow interface. */
  public interface GreetingWorkflow {
    /** @return greeting string */
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 600000, taskList = TASK_LIST_PARENT)
    String getGreeting(String name);
  }

  /** The child workflow interface. */
  public interface GreetingChild {
    /** @return greeting string */
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 600000, taskList = TASK_LIST_CHILD)
    String composeGreeting(String greeting, String name);
  }

  /** Activity interface is just to call external service and doNotCompleteActivity. */
  public interface GreetingActivities {
    @ActivityMethod(scheduleToCloseTimeoutSeconds = 600000)
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
      GreetingChild child = Workflow.newChildWorkflowStub(GreetingChild.class, options);

      // This is a blocking call that returns only after the child has completed.
      Promise<String> greeting = Async.function(child::composeGreeting, "Hello", name);
      // Do something else here.
      return greeting.get(); // blocks waiting for the child to complete.
      // return "Child started";
    }
  }

  /**
   * The child workflow implementation. A workflow implementation must always be public for the
   * Cadence library to be able to create instances.
   */
  public static class GreetingChildImpl implements GreetingChild {
    public String composeGreeting(String greeting, String name) {
      GreetingActivities activities = Workflow.newActivityStub(GreetingActivities.class);
      Promise greetingActivities = Async.function(activities::composeGreeting, greeting, name);
      return greetingActivities.get() + " " + name + "!";
    }
  }

  static class GreetingActivitiesImpl implements GreetingActivities {
    @Override
    public String composeGreeting(String greeting, String name) {
      System.out.println("st===1 ");
      byte[] taskToken = Activity.getTaskToken();
      sendRestRequest(taskToken);
      System.out.println("st===2 ");
      System.out.println(Hex.encodeHexString(taskToken));
      Activity.doNotCompleteOnReturn();
      System.out.println("st===3 ");
      return "";
    }
  }

  private static void sendRestRequest(byte[] taskToken) {
    try {
      System.out.println("st===4");
      URL url = new URL("http://127.0.0.1:8090/api/cadence/async");
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setDoOutput(true);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestMethod("PUT");
      connection.setRequestProperty("Content-Type", "application/octet-stream");

      OutputStream os = connection.getOutputStream();
      os.write(taskToken);
      os.flush();

      connection.getResponseCode();
      connection.disconnect();
      System.out.println("st===5");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) {
    // Start a worker that hosts both parent and child workflow implementations.
    Scope scope =
        new RootScopeBuilder()
            .reporter(new CadenceClientStatsReporter())
            .reportEvery(Duration.ofSeconds(1));

    Worker.Factory factory =
        new Worker.Factory(
            "127.0.0.1",
            7933,
            DOMAIN,
            new Worker.FactoryOptions.Builder().setMetricScope(scope).build());
    Worker workerParent =
        factory.newWorker(
            TASK_LIST_PARENT, new WorkerOptions.Builder().setMetricsScope(scope).build());
    workerParent.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);

    Worker workerChild =
        factory.newWorker(
            TASK_LIST_CHILD, new WorkerOptions.Builder().setMetricsScope(scope).build());
    workerChild.registerWorkflowImplementationTypes(GreetingChildImpl.class);
    workerChild.registerActivitiesImplementations(new GreetingActivitiesImpl());

    // Start listening to the workflow and activity task lists.
    factory.start();

    // Start a workflow execution. Usually this is done from another program.
    WorkflowClient workflowClient = WorkflowClient.newInstance("127.0.0.1", 7933, DOMAIN);
    // Get a workflow stub using the same task list the worker uses.
    GreetingWorkflow parentWorkflow;

    // Execute a workflow waiting for it to complete.
    while (true) {
      parentWorkflow = workflowClient.newWorkflowStub(GreetingWorkflow.class);
      WorkflowClient.start(parentWorkflow::getGreeting, "World");
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    // System.exit(0);

  }
}
