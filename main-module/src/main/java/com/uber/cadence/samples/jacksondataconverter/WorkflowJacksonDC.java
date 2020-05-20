package com.uber.cadence.samples.jacksondataconverter;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.samples.jacksondataconverter.dto.ErrorBodyImpl;
import com.uber.cadence.samples.jacksondataconverter.dto.MessageResponse;
import com.uber.cadence.samples.jacksondataconverter.dto.MessageResponseImpl;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.workflow.Async;
import com.uber.cadence.workflow.Promise;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import lombok.SneakyThrows;

public class WorkflowJacksonDC {

  static final String TASK_LIST = "WorkflowJacksonDC";
  private static final ObjectMapper jackson = new ObjectMapper();

  public interface ResponseWorkflow {

    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 10, taskList = TASK_LIST)
    String handleResponse(MessageResponse response);
  }

  public interface ResponseChild {

    @WorkflowMethod
    String composeResponse(String prefix, MessageResponse response);
  }

  public static class ResponseWorkflowImpl implements ResponseWorkflow {

    @Override
    @SneakyThrows
    public String handleResponse(MessageResponse response) {
      ResponseChild child = Workflow.newChildWorkflowStub(ResponseChild.class);
      System.out.println("inside parent wf: " + jackson.writeValueAsString(response));
      Promise<String> greeting = Async.function(child::composeResponse, "Hello", response);
      return greeting.get();
    }
  }

  public static class ResponseChildImpl implements ResponseChild {

    @Override
    @SneakyThrows
    public String composeResponse(String prefix, MessageResponse response) {
      return prefix + " " + jackson.writeValueAsString(response) + "!";
    }
  }

  public static void main(String[] args) {

    Worker.Factory factory = new Worker.Factory(DOMAIN);
    Worker worker = factory.newWorker(TASK_LIST);
    worker.registerWorkflowImplementationTypes(ResponseWorkflowImpl.class, ResponseChildImpl.class);

    factory.start();

    WorkflowClient workflowClient = WorkflowClient.newInstance(DOMAIN);
    ResponseWorkflow workflow = workflowClient.newWorkflowStub(ResponseWorkflow.class);

    final MessageResponseImpl response = new MessageResponseImpl();
    response.setRequestId("12345");
    response.setError(error("simple_error"));
    response.setErrorList(Lists.newArrayList(error("code1"), error("code2")));

    String greeting = workflow.handleResponse(response);
    System.out.println(greeting);
    System.exit(0);
  }

  public static ErrorBodyImpl error(String code) {
    final ErrorBodyImpl errorBody = new ErrorBodyImpl();
    errorBody.setCode(code);
    errorBody.setDescription(String.format("Description for code '%s'", code));
    errorBody.setMessages(Lists.newArrayList("hello", "world!"));
    return errorBody;
  }
}
