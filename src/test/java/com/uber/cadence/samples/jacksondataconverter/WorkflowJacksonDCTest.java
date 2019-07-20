package com.uber.cadence.samples.jacksondataconverter;

import static com.uber.cadence.samples.jacksondataconverter.WorkflowJacksonDC.error;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.client.WorkflowClientOptions.Builder;
import com.uber.cadence.samples.jacksondataconverter.WorkflowJacksonDC.ResponseChildImpl;
import com.uber.cadence.samples.jacksondataconverter.WorkflowJacksonDC.ResponseWorkflow;
import com.uber.cadence.samples.jacksondataconverter.WorkflowJacksonDC.ResponseWorkflowImpl;
import com.uber.cadence.samples.jacksondataconverter.dto.MessageResponseImpl;
import com.uber.cadence.testing.TestEnvironmentOptions;
import com.uber.cadence.testing.TestWorkflowEnvironment;
import com.uber.cadence.worker.Worker;
import lombok.SneakyThrows;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class WorkflowJacksonDCTest {

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
  private ObjectMapper jackson = new ObjectMapper();

  MessageResponseImpl response;

  @Before
  public void setUp() {
    final TestEnvironmentOptions testEnvOpts =
        new TestEnvironmentOptions.Builder()
            .setDataConverter(JacksonDataConverter.getInstance())
            .build();
    testEnv = TestWorkflowEnvironment.newInstance(testEnvOpts);
    worker = testEnv.newWorker(WorkflowJacksonDC.TASK_LIST);

    final WorkflowClientOptions opts =
        new Builder().setDataConverter(JacksonDataConverter.getInstance()).build();
    workflowClient = testEnv.newWorkflowClient(opts);

    response = new MessageResponseImpl();
    response.setRequestId("12345");
    response.setError(error("simple_error"));
    response.setErrorList(Lists.newArrayList(error("code1"), error("code2")));
  }

  @After
  public void tearDown() {
    testEnv.close();
  }

  @Test
  @SneakyThrows
  public void testChild() {
    worker.registerWorkflowImplementationTypes(ResponseWorkflowImpl.class, ResponseChildImpl.class);

    testEnv.start();

    ResponseWorkflow workflow = workflowClient.newWorkflowStub(ResponseWorkflow.class);
    String json =
        "{\"requestId\":\"12345\",\"new_field\": \"test_value\", \"error\":{\"code\":\"simple_error\",\"description\":\"Description for code 'simple_error'\",\"messages\":[\"hello\",\"world!\"]},\"errorList\":[{\"code\":\"code1\",\"description\":\"Description for code 'code1'\",\"messages\":[\"hello\",\"world!\"]},{\"code\":\"code2\",\"description\":\"Description for code 'code2'\",\"messages\":[\"hello\",\"world!\"]}]}";
    final MessageResponseImpl fromJson = jackson.readValue(json, MessageResponseImpl.class);
    final String newFieldKey = "new_field";
    final String newFieldValueExpected = "test_value";
    final Object newFieldValueActual = fromJson.getAdditionalProperties().get(newFieldKey);
    assertEquals(newFieldValueExpected, newFieldValueActual);
    System.out.println(newFieldKey + ": " + newFieldValueActual);
    System.out.println("from json: " + fromJson);
    System.out.println("from json to json: " + jackson.writeValueAsString(fromJson));

    String result = workflow.handleResponse(fromJson);
    System.out.println("from workflow: " + result);
    assertTrue(result.contains(fromJson.getRequestId()));
    assertTrue(result.contains(fromJson.getError().getCode()));
    assertTrue(result.contains(newFieldKey));
    assertTrue(result.contains(newFieldValueExpected));
  }
}
