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

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.converter.JsonDataConverter;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerOptions;
import com.uber.cadence.workflow.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

/** Demonstrates a child workflow. Requires a local instance of the Cadence server to be running. */
public class HelloChildWithInheritedArgs {

  static final String TASK_LIST = "HelloChild";

  public static class GreetingBaseArgs {
    private String name;

    public GreetingBaseArgs(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  public interface Extendable {
    Map<String, Object> getExtension();
    void setExtension(Map<String, Object> extension);
  }

  public static class GreetingBaseArgsExtended extends GreetingBaseArgs implements Extendable {

    private Map<String,Object> extension = new HashMap<>();

    public GreetingBaseArgsExtended(String name) {
      super(name);
    }

    public Map<String, Object> getExtension() {
      return extension;
    }

    public void setExtension(Map<String, Object> extension) {
      this.extension = extension;
    }

  }

  // Could not managed to bound TOutput to both TInput and Extendable. It must extends both, actually.
  public static class ExtendableTypeAdapterFactory<TInput, TOutput extends Extendable> implements TypeAdapterFactory {

    private Class<TInput> inputClass;
    private Class<TOutput> outputClass;

    public ExtendableTypeAdapterFactory(Class<TInput> inputClass, Class<TOutput> outputClass) {

      this.inputClass = inputClass;
      this.outputClass = outputClass;
    }

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
      // directly inputClass (on read) and for outputClass (on write)
      if (inputClass != type.getRawType() && outputClass != type.getRawType()) {
        return null;
      }
      return new ExtendableTypeAdapter(gson, this, inputClass, outputClass).nullSafe();
    }
  }

  public static class ExtendableTypeAdapter<T> extends TypeAdapter<T> {

    private final Gson gson;
    private final TypeAdapterFactory skipPast;
    private final Class inputClass;
    private final Class outputClass;

    public ExtendableTypeAdapter(Gson gson, TypeAdapterFactory skipPast, Class inputClass, Class outputClass) {
      this.gson = gson;
      this.skipPast = skipPast;
      this.inputClass = inputClass;
      this.outputClass = outputClass;
    }

    @Override
    public void write(JsonWriter out, T value) throws IOException {
      //write known properties
      TypeAdapter exceptionTypeAdapter =
          gson.getDelegateAdapter(skipPast, TypeToken.get(value.getClass()));
      JsonObject object = exceptionTypeAdapter.toJsonTree(value).getAsJsonObject();
      TypeAdapter<JsonElement> elementAdapter = gson.getAdapter(JsonElement.class);

      //write extended properties
      Map<String, Object> extendedProperties = ((Extendable) value).getExtension();
      for (Map.Entry<String, Object> entry : extendedProperties.entrySet()) {
        object.add(entry.getKey(), gson.toJsonTree(entry.getValue()));
      }

      elementAdapter.write(out, object);
    }

    @Override
    public T read(JsonReader in) throws IOException {
      // read known fields
      TypeAdapter<JsonElement> elementAdapter = gson.getAdapter(JsonElement.class);
      JsonObject object = elementAdapter.read(in).getAsJsonObject();
      TypeAdapter exceptionTypeAdapter =
          gson.getDelegateAdapter(skipPast, TypeToken.get(outputClass));
      Extendable result = (Extendable)exceptionTypeAdapter.fromJsonTree(object);

      // get names of the known properties
      Set<String> knownProperties = Arrays.stream(inputClass.getFields())
          .map(Field::getName)
          .collect(Collectors.toSet());

      // read extended properties
      Map<String, Object> extendedProperties = object.keySet()
          .stream()
          .filter(x -> !knownProperties.contains(x))
          .collect(Collectors.toMap(key -> key, object::get));
      result.setExtension(extendedProperties);

      return (T)result;
    }
  }

  public static class GreetingChildArgs extends GreetingBaseArgs {
    private String otherName;

    public GreetingChildArgs(String name, String otherName) {
      super(name);
      this.otherName = otherName;
    }

    public String getOtherName() {
      return otherName;
    }

    public void setOtherName(String otherName) {
      this.otherName = otherName;
    }
  }

  /** The parent workflow interface. */
  public interface GreetingWorkflow {
    /** @return greeting string */
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 10, taskList = TASK_LIST)
    String getGreeting(GreetingBaseArgs args);
  }

  /** The child workflow interface. */
  public interface GreetingChild {
    @WorkflowMethod
    String composeGreeting(String greeting, GreetingChildArgs args);
  }

  /** GreetingWorkflow implementation that calls GreetingsActivities#printIt. */
  public static class GreetingWorkflowImpl implements GreetingWorkflow {

    @Override
    public String getGreeting(GreetingBaseArgs args) {
      // Workflows are stateful. So a new stub must be created for each new child.
      ChildWorkflowStub child = Workflow.newUntypedChildWorkflowStub("GreetingChild::composeGreeting");

      return child.execute(String.class, "Hello", args);
    }
  }

  /**
   * The child workflow implementation. A workflow implementation must always be public for the
   * Cadence library to be able to create instances.
   */
  public static class GreetingChildImpl implements GreetingChild {
    @Override
    public String composeGreeting(String greeting, GreetingChildArgs args) {
      return greeting + " " + args.getName() + " - " + args.getOtherName() + "!";
    }
  }

  public static void main(String[] args) {
    // Start a worker that hosts both parent and child workflow implementations.
    Worker.Factory factory = new Worker.Factory(DOMAIN);
    Worker worker = factory.newWorker(
        TASK_LIST,
        new WorkerOptions.Builder()
            .setDataConverter(new JsonDataConverter(builder ->
                builder.registerTypeAdapterFactory(
                    new ExtendableTypeAdapterFactory(GreetingBaseArgs.class, GreetingBaseArgsExtended.class))
            ))
            .build());
    worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class, GreetingChildImpl.class);
    // Start listening to the workflow task list.
    factory.start();

    // Start a workflow execution. Usually this is done from another program.
    WorkflowClient workflowClient = WorkflowClient.newInstance(DOMAIN);
    // Get a workflow stub using the same task list the worker uses.
    GreetingWorkflow workflow = workflowClient.newWorkflowStub(GreetingWorkflow.class);
    // Execute a workflow waiting for it to complete.
    String greeting = workflow.getGreeting(new GreetingChildArgs("World", "from a child class"));
    System.out.println(greeting);
    System.exit(0);
  }
}
