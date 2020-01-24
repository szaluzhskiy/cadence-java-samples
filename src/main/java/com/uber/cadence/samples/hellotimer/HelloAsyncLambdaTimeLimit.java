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
import java.time.temporal.ChronoUnit;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Demonstrates payment gw interation which must be completed after timeout. Requires a local
 * instance of the Cadence service to be running.
 */
public class HelloAsyncLambdaTimeLimit {

  static final String TASK_LIST = "HelloWorkflowCompleteByTimeout";
  static final Integer ACTIVITY_DURATION_SEC = 10;

  public interface PaymentWorkflow {
    /** @return payment status */
    @WorkflowMethod
    String payment(
        String amount,
        String account,
        Function<String, String> callbackPositive,
        Function<Throwable, String> callbackNegative);
  }

  public interface PaymentActivity {
    String makePayment(String amount, String account);
  }

  public interface ReportPaymentActivities {
    String report(String amount, String account, String status);
  }

  /**
   * PaymentWorkflow implementation that demonstrates how to use retry for workflow and activity
   * stub configured with {@link RetryOptions}. And demonstrates how to complete workflow by
   * external timer.
   */
  public static class PaymentWorkflowImpl implements PaymentWorkflow {

    /**
     * To enable activity retry set {@link RetryOptions} on {@link ActivityOptions}. It also works
     * for activities invoked through and or child workflows.
     */
    private final PaymentActivity paymentActivity =
        Workflow.newActivityStub(
            PaymentActivity.class,
            new ActivityOptions.Builder()
                .setScheduleToStartTimeout(Duration.ofMillis(1000))
                .setStartToCloseTimeout(Duration.ofSeconds(ACTIVITY_DURATION_SEC - 1000))
                .setRetryOptions(
                    new RetryOptions.Builder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setBackoffCoefficient(1.1)
                        .setExpiration(Duration.ofSeconds(ACTIVITY_DURATION_SEC))
                        .setDoNotRetry(IllegalArgumentException.class)
                        .build())
                .build());

    @Override
    public String payment(
        String amount,
        String account,
        Function<String, String> callbackPositive,
        Function<Throwable, String> callbackNegative) {
      // Async.invoke accepts not only activity or child workflow method references
      // but lambda functions as well. Behind the scene it allocates a thread
      // to execute it asynchronously.
      Promise<String> result =
          Async.function(
              () -> {
                return paymentActivity.makePayment(amount, account);
              });
      String resStr;
      try {
        resStr =
            result
                .thenApply(res -> callbackPositive.apply(res))
                .exceptionally(e -> callbackNegative.apply(e))
                .get(ACTIVITY_DURATION_SEC, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        resStr = callbackNegative.apply(e);
      }
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
      System.out.println(">>> Start makePayment");
      // 1. never ends task
      longRunningTask(null);

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

  private static void longRunningTask(Duration duration) {
    if (duration == null) {
      int i = 0;
      while (true) {
        i = i % 2 == 0 ? i - 1 : i + 1;
      }
    } else {
      ExecutorService es = Executors.newFixedThreadPool(1);
      Future<Void> f =
          es.submit(
              () -> {
                int i = 0;
                while (true) {
                  i = i % 2 == 0 ? i - 1 : i + 1;
                }
              });
      try {
        f.get(duration.get(ChronoUnit.SECONDS), TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        e.printStackTrace();
      } catch (ExecutionException e) {
        e.printStackTrace();
      } catch (TimeoutException e) {
        e.printStackTrace();
      }
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
        workflow.payment(
            "1000",
            "3234-0989-0988-0988",
            (r) -> {
              System.out.println(r);
              return r;
            },
            (e) -> {
              System.out.println(e);
              return "PAYMENT FAILED";
            });
    System.out.println(paymentResult);
    System.exit(0);
  }
}
