package com.uber.cadence.samples.helloRetrySaga;

import static com.uber.cadence.samples.helloRetrySaga.HelloActivityRetry.TASK_LIST_CHILD;

import com.uber.cadence.activity.ActivityOptions;
import com.uber.cadence.common.RetryOptions;
import com.uber.cadence.workflow.ChildWorkflowOptions;
import com.uber.cadence.workflow.Functions;
import com.uber.cadence.workflow.Saga;
import com.uber.cadence.workflow.Workflow;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GreetingWorkflow implementation that demonstrates activity stub configured with {@link
 * RetryOptions}.
 */
public class GreetingWorkflowImpl implements GreetingWorkflow {
  AtomicInteger attempt = new AtomicInteger();
  static final String TASK_LIST = "HelloActivityRetry";

  /**
   * To enable activity retry set {@link RetryOptions} on {@link ActivityOptions}. It also works for
   * activities invoked through {@link com.uber.cadence.workflow.Async#invoke(Functions.Proc)} and
   * for child workflows.
   */
  private final GreetingActivities activities =
      Workflow.newActivityStub(
          GreetingActivities.class,
          new ActivityOptions.Builder()
              .setScheduleToCloseTimeout(Duration.ofSeconds(10))
              .setRetryOptions(
                  new RetryOptions.Builder()
                      .setInitialInterval(Duration.ofSeconds(1))
                      .setBackoffCoefficient(1.0)
                      .setExpiration(Duration.ofSeconds(30))
                      .setDoNotRetry(IllegalArgumentException.class)
                      .build())
              .build());

  @Override
  public String getGreeting(String name, String workflowId) {
    // This is a blocking call that returns only after activity is completed.
    System.out.println("Start workflow " + workflowId);
    Saga saga = new Saga(new Saga.Options.Builder().setContinueWithError(true).build());

    try {
      /*    activities.composeGreeting(workflowId, attempt);
      saga.addCompensation(
          () -> {
            System.out.println("SAGA Compensation_1, wfid=" + workflowId);
          });*/

      ChildWorkflowOptions childWorkflowOptions =
          new ChildWorkflowOptions.Builder()
              .setTaskList(TASK_LIST_CHILD)
              .setExecutionStartToCloseTimeout(Duration.ofDays(1))
              .build();

      GreetingChildWorkflow child =
          Workflow.newChildWorkflowStub(GreetingChildWorkflow.class, childWorkflowOptions);
      child.getChildGreeting("1", workflowId);
    } catch (Exception ex) {
      System.out.println("CHILD WF FAILED");
      saga.compensate();
      throw ex;
    }

    return "WF FINISHED";
  }
}
