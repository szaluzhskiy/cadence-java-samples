package com.uber.cadence.samples.activityinheritance;

import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.samples.activityinheritance.case1.ActivityInheritanceFail1;
import com.uber.cadence.samples.activityinheritance.case1.ActivityInheritanceWorkflowFail1Impl;
import com.uber.cadence.samples.activityinheritance.case2.ActivityInheritanceFail2;
import com.uber.cadence.samples.activityinheritance.case2.ActivityInheritanceWorkflowFail2Impl;
import com.uber.cadence.testing.TestWorkflowEnvironment;
import com.uber.cadence.worker.Worker;
import java.time.Duration;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class ActivityInheritanceTest {

  /**
   * Prints workflow histories under test in case of a test failure.
   */
  @Rule
  public TestWatcher watchman =
      new TestWatcher() {
        @Override
        protected void failed(Throwable e, Description description) {
          if (testEnv != null) {
            System.err.println(testEnv.getDiagnostics());
            testEnv.close();
          }
        }
      };

  private TestWorkflowEnvironment testEnv;
  private Worker worker;
  private WorkflowClient workflowClient;

  @Before
  public void setUp() {
    testEnv = TestWorkflowEnvironment.newInstance();
    worker = testEnv.newWorker(Constants.TASK_LIST);
    workflowClient = testEnv.newWorkflowClient();
  }

  @After
  public void tearDown() {
    testEnv.close();
  }

  @Test
  @SneakyThrows
  public void test1() {
    worker.registerWorkflowImplementationTypes(ActivityInheritanceWorkflowFail1Impl.class);
    worker.registerActivitiesImplementations(new ActivityInheritanceFail1.TypeAActivityImpl(), new ActivityInheritanceFail1.TypeBActivityImpl());
    testEnv.start();

    WorkflowOptions workflowOptions =
        new WorkflowOptions.Builder()
            .setTaskList(Constants.TASK_LIST)
            .setExecutionStartToCloseTimeout(Duration.ofSeconds(5))
            .build();
    ActivityInheritanceWorkflow workflow = workflowClient
        .newWorkflowStub(ActivityInheritanceWorkflow.class, workflowOptions);

    List<String> result = workflow.send("request123");
    System.out.println(result);
  }

  @Test
  @SneakyThrows
  public void test2() {
    worker.registerWorkflowImplementationTypes(ActivityInheritanceWorkflowFail2Impl.class);
    worker.registerActivitiesImplementations(new ActivityInheritanceFail2.TypeAActivityImpl(), new ActivityInheritanceFail2.TypeBActivityImpl());
    testEnv.start();

    WorkflowOptions workflowOptions =
        new WorkflowOptions.Builder()
            .setTaskList(Constants.TASK_LIST)
            .setExecutionStartToCloseTimeout(Duration.ofSeconds(5))
            .build();
    ActivityInheritanceWorkflow workflow = workflowClient
        .newWorkflowStub(ActivityInheritanceWorkflow.class, workflowOptions);

    List<String> result = workflow.send("request123");
    System.out.println(result);
  }
}
