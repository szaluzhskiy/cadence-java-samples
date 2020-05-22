package com.example.client;

import com.uber.cadence.DomainAlreadyExistsError;
import com.uber.cadence.RegisterDomainRequest;
import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.serviceclient.IWorkflowService;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.workflow.WorkflowMethod;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import static com.example.client.SampleConstants.*;

@Slf4j
@Component
public class Client implements ApplicationRunner {

  @Override
  public void run(ApplicationArguments args) {
      registerDomain();
      runClient();
  }

  private void runClient() {
      // Start a workflow execution. Usually this is done from another program.
      WorkflowClient workflowClient = WorkflowClient.newInstance(
              "127.0.0.1",
              7933,
              DOMAIN);
      // Get a workflow stub using the same task list the worker uses.
      // Execute a workflow waiting for it to complete.
      GreetingWorkflow parentWorkflow;

      while (true) {
          try {
              parentWorkflow = workflowClient.newWorkflowStub(GreetingWorkflow.class);
              WorkflowExecution we = WorkflowClient.start(parentWorkflow::getGreeting, "World");

              Thread.sleep(500);

          } catch (Exception e) {
              log.error("Error occurred", e);
          }
      }
  }

    private void registerDomain() {
        IWorkflowService cadenceService = new WorkflowServiceTChannel();
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
        }
    }

    /** The parent workflow interface. */
    public interface GreetingWorkflow {
        /** @return greeting string */
        @WorkflowMethod(executionStartToCloseTimeoutSeconds = 60000, taskList = TASK_LIST_PARENT)
        String getGreeting(String name);
    }
}
