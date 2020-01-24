package com.uber.cadence.samples.hellotimer;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

import com.uber.cadence.activity.ActivityOptions;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.common.RetryOptions;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.workflow.Async;
import com.uber.cadence.workflow.Promise;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import java.time.Duration;
import java.util.concurrent.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Demonstrates payment gw interation which must be completed after timeout. Requires a local
 * instance of the Cadence service to be running.
 */
@Slf4j
public class HelloAsyncLambdaTimeLimit {

  static final String TASK_LIST = "HelloWorkflowCompleteByTimeout";
  static final Integer ACTIVITY_DURATION_EXTERNAL_SEC = 5;
  static final Integer ACTIVITY_DURATION_INTERNAL_SEC = 60;

  public interface CallbackService {
    String success(String result);

    String fail(Throwable ex);
  }

  public interface PaymentWorkflow {
    /** @return payment status */
    @WorkflowMethod
    String payment(String amount, String account, CallbackServiceImpl callback);
  }

  public interface PaymentActivity {
    String makePayment(String amount, String account);
  }

  /**
   * PaymentWorkflow implementation that demonstrates how to use retry for workflow and activity
   * stub configured with {@link RetryOptions}. And demonstrates how to complete workflow by
   * external timer.
   */
  public static class PaymentWorkflowImpl implements PaymentWorkflow {

    public PaymentWorkflowImpl() {}

    /**
     * To enable activity retry set {@link RetryOptions} on {@link ActivityOptions}. It also works
     * for activities invoked through and or child workflows.
     */
    private final PaymentActivity paymentActivity =
        Workflow.newActivityStub(
            PaymentActivity.class,
            new ActivityOptions.Builder()
                .setScheduleToCloseTimeout(Duration.ofSeconds(ACTIVITY_DURATION_EXTERNAL_SEC))
                .setRetryOptions(
                    new RetryOptions.Builder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setBackoffCoefficient(1.1)
                        .setMaximumAttempts(1000)
                        // .setExpiration(Duration.ofSeconds(ACTIVITY_DURATION_EXTERNAL_SEC))
                        .setDoNotRetry(IllegalArgumentException.class)
                        .build())
                .build());

    @Override
    public String payment(String amount, String account, CallbackServiceImpl callback) {

      // Async.invoke accepts not only activity or child workflow method references
      // but lambda functions as well. Behind the scene it allocates a thread
      // to execute it asynchronously.
      Promise<String> result =
          Async.function(
              () -> {
                log.info("7");
                String s = paymentActivity.makePayment(amount, account);
                log.info("res {}", s);
                return s;
              });
      String resStr;
      try {
        log.info("1");
        resStr = result.get(ACTIVITY_DURATION_EXTERNAL_SEC, TimeUnit.SECONDS);
        log.info("2");
      } catch (TimeoutException e) {
        log.info("3");
        resStr = callback.fail(e);
        log.info("4");
      }
      log.info("5");
      return resStr;
    }
  }

  /*
    @Override
    public String payment(Duration operationDuration, String amount, String account) {
      long startTS = System.currentTimeMillis();
      System.out.println(">>> Started >>>");
      Promise timer1 = Workflow.newTimer(operationDuration);

      timer1
          .thenApply(
              res -> {
                long finishTS = System.currentTimeMillis();
                System.out.println("<<< After timer <<<, " + (finishTS - startTS) / 1000);
                String res2 = paymentActivity.makePayment(amount, account);
                System.out.println("Finished with payment result = " + res2);
                return res2;
              })
          .exceptionally(
              e -> {
                System.out.println("Failed with payment result = " + e);
                return "payment failed";
              })
          .get();
      long finishTS = System.currentTimeMillis();
      System.out.println("<<< Finished <<<, " + (finishTS - startTS) / 1000);
      return "WF DONE";
    }
  }
  */

  static class PaymentActivitiesImpl implements PaymentActivity {

    @Override
    public String makePayment(String amount, String account) {
      // 1. never ends task
      // longRunningTask(null);
      longRunningTask(Duration.ofSeconds(ACTIVITY_DURATION_INTERNAL_SEC));

      // 2. Throw some runtime exception, emulation of RestClientException
      // throw new RuntimeException("RestClientException");

      // 3. Random duration task
      //

      // 6. Non retry on IllegalArgumentException
      // throw new IllegalArgumentException("This property is not supported");

      //
      // System.out.println("makePayment finished " + ((System.currentTimeMillis() - startTS) /
      // 1000));
      return "PAYMENT OF AMOUNT " + amount + " TO ACCOUNT " + account + " DONE";
    }
  }

  @SneakyThrows
  private static void longRunningTask(Duration duration) {
    if (duration == null) {
      System.out.println("TASK STARTED");
      int i = 0;
      while (true) {
        i = i % 2 == 0 ? i - 1 : i + 1;
      }
    } else {
      System.out.println("TASK STARTED");
      log.info("6-1");
      Thread.sleep(1000 * 50);
      log.info("6-2");
      System.out.println("TASK FINISHED");
    }
  }

  public static void main(String[] args) {
    // Start a worker that hosts both workflow and activity implementations.
    Worker.Factory factory = new Worker.Factory(DOMAIN);
    Worker worker = factory.newWorker(TASK_LIST);
    // Workflows are stateful. So you need a type to create instances.
    worker.registerWorkflowImplementationTypes(PaymentWorkflowImpl.class);
    // Activities are stateless and thread safe. So a shared instance is used.
    worker.registerActivitiesImplementations(new PaymentActivitiesImpl());
    // Start listening to the workflow and activity task lists.
    factory.start();

    // Start a workflow execution. Usually this is done from another program.
    WorkflowClient workflowClient = WorkflowClient.newInstance(DOMAIN);
    // Get a workflow stub using the same task list the worker uses.
    WorkflowOptions workflowOptions =
        new WorkflowOptions.Builder()
            .setTaskList(TASK_LIST)
            .setExecutionStartToCloseTimeout(Duration.ofSeconds(600))
            .build();
    PaymentWorkflow workflow =
        workflowClient.newWorkflowStub(PaymentWorkflow.class, workflowOptions);
    // Execute a workflow waiting for it to complete.
    String paymentResult =
        workflow.payment("1000", "3234-0989-0988-0988", new CallbackServiceImpl());
    System.out.println(paymentResult);
    // System.exit(0);
  }
}
