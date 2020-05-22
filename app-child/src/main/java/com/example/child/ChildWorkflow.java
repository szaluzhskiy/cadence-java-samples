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

package com.example.child;

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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import static com.example.child.SampleConstants.*;

/**
 * Demonstrates a child workflow. Requires a local instance of the Cadence server to be running.
 *
 * <p>Based on HelloChildWithExternalRest class
 */
@Component
@Slf4j
public class ChildWorkflow implements ApplicationRunner {

  @Override
  public void run(ApplicationArguments args) {
    registerDomain();
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
            new Worker.FactoryOptions.Builder().setMetricScope(scope).build());

    Worker workerChild =
        factory.newWorker(
            TASK_LIST_CHILD, new WorkerOptions.Builder().setMetricsScope(scope).build());
    workerChild.registerWorkflowImplementationTypes(GreetingChildImpl.class);
    workerChild.registerActivitiesImplementations(new GreetingActivitiesImpl());

    // Start listening to the workflow and activity task lists.
    factory.start();
    log.info("Started factory");
  }

  private void registerDomain() {
    IWorkflowService cadenceService = new WorkflowServiceTChannel( System.getenv(CADENCE_HOST),
            Integer.parseInt(System.getenv(CADENCE_PORT)));
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

  /** The child workflow interface. */
  public interface GreetingChild {
    /** @return greeting string */
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 60000, taskList = TASK_LIST_CHILD)
    String composeGreeting(String greeting, String name);
  }

  /** Activity interface is just to call external service and doNotCompleteActivity. */
  public interface GreetingActivities {
    @ActivityMethod(scheduleToStartTimeoutSeconds = 60000, startToCloseTimeoutSeconds = 60)
    String composeGreeting(String greeting, String name);
  }

  /**
   * The child workflow implementation. A workflow implementation must always be public for the
   * Cadence library to be able to create instances.
   */
  public static class GreetingChildImpl implements GreetingChild {
    public String composeGreeting(String greeting, String name) {
      long startSW = System.nanoTime();
      List<Promise<String>> activities = new ArrayList<>();
      RetryOptions ro =
          new RetryOptions.Builder()
              .setInitialInterval(java.time.Duration.ofSeconds(30))
              .setMaximumInterval(java.time.Duration.ofSeconds(30))
              .setMaximumAttempts(2)
              .build();
      ActivityOptions ao =
          new ActivityOptions.Builder().setTaskList(TASK_LIST_CHILD).setRetryOptions(ro).build();
      for (int i = 0; i < 10; i++) {
        GreetingActivities activity = Workflow.newActivityStub(GreetingActivities.class, ao);
        activities.add(Async.function(activity::composeGreeting, greeting + i, name));
      }
      Promise greetingActivities = Promise.allOf(activities);
      String result = greetingActivities.get() + " " + name + "!";
      System.out.println(
          "Duration of childwf - " + Duration.between(startSW, System.nanoTime()).getSeconds());

      return result;
    }
  }

  static class GreetingActivitiesImpl implements GreetingActivities {
    @Override
    public String composeGreeting(String greeting, String name) {
      byte[] taskToken = Activity.getTaskToken();
      sendRestRequest(taskToken);
      Activity.doNotCompleteOnReturn();

      return "Activity paused";
    }
  }

  private static void sendRestRequest(byte[] taskToken) {
    try {
      URL url = new URL(System.getenv(GO_SERVICE_URL));
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

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
