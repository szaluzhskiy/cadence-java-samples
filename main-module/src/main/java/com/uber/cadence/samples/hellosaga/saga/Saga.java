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

package com.uber.cadence.samples.hellosaga.saga;

import com.uber.cadence.activity.ActivityOptions;
import com.uber.cadence.internal.common.LambdaUtils;
import com.uber.cadence.workflow.*;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

public class Saga {

  private Stack<CompensationItem> compensations = new Stack<>();

  public <CA1, R> Functions.Func1<Result<R, CA1>, R> withCompensation(
      Functions.Proc1<CA1> compensationProc) {
    return activityResult -> {
      compensations.push(
          new CompensationItem(
              getExecutionName(compensationProc),
              ExecutionType.ACTIVITY,
              activityResult.compensationArgs,
              null));
      return activityResult.getResult();
    };
  }

  public <CA1, R> R withCompensation(
      Result<R, CA1> activityResult, Functions.Proc1<CA1> compensationProc) {
    compensations.push(
        new CompensationItem(
            getExecutionName(compensationProc),
            ExecutionType.ACTIVITY,
            activityResult.compensationArgs,
            null));
    return activityResult.getResult();
  }

  public <A1, CA1> void executeProc(
      Functions.Func1<A1, Compensation<CA1>> proc, A1 arg1, Functions.Proc1<CA1> compensationProc) {
    Compensation<CA1> result = proc.apply(arg1);
    compensations.push(
        new CompensationItem(
            getExecutionName(compensationProc),
            ExecutionType.ACTIVITY,
            result.compensationArgs,
            null));
  }

  public <A1, CA1> Promise<Void> executeProcAsync(
      Functions.Func1<A1, Compensation<CA1>> proc, A1 arg1, Functions.Proc1<CA1> compensationProc) {
    return Async.function(proc, arg1)
        .thenApply(
            result -> {
              compensations.push(
                  new CompensationItem(
                      getExecutionName(compensationProc),
                      ExecutionType.ACTIVITY,
                      result.compensationArgs,
                      null));
              return null;
            });
  }

  public <A1, CA1, R> Promise<R> executeFuncAsync(
      Functions.Func1<A1, Result<R, CA1>> func, A1 arg1, Functions.Proc1<CA1> compensationProc) {
    return Async.function(func, arg1)
        .thenApply(
            result -> {
              compensations.push(
                  new CompensationItem(
                      getExecutionName(compensationProc),
                      ExecutionType.ACTIVITY,
                      result.compensationArgs,
                      null));
              return result.getResult();
            });
  }

  public <A1, CA1, R> R executeFunc(
      Functions.Func1<A1, Result<R, CA1>> func, A1 arg1, Functions.Proc1<CA1> compensationProc) {
    Result<R, CA1> result = func.apply(arg1);
    compensations.push(
        new CompensationItem(
            getExecutionName(compensationProc),
            ExecutionType.ACTIVITY,
            result.compensationArgs,
            null));
    return result.getResult();
  }

  public <A1, R> Promise<R> executeChildFuncAsync(
      Functions.Func1<A1, WorkflowResult<R>> func, A1 arg1) {
    return Async.function(func, arg1)
        .thenApply(
            result -> {
              compensations.push(
                  new CompensationItem(
                      null,
                      ExecutionType.CHILD_WORKFLOW,
                      result.compensationArgs,
                      getChildWfTaskList(func)));
              return result.getResult();
            });
  }

  public List<CompensationItem> exportCompensations() {
    return new ArrayList<>(compensations);
  }

  public void importCompensations(List<CompensationItem> compensations) {
    this.compensations.addAll(compensations);
  }

  public void compensate() {

    ActivityStub activityExecutor =
        Workflow.newUntypedActivityStub(
            new ActivityOptions.Builder()
                .setScheduleToCloseTimeout(Duration.ofSeconds(10))
                .setScheduleToStartTimeout(Duration.ofSeconds(10))
                .build());

    while (!compensations.empty()) {
      CompensationItem comp = compensations.pop();
      switch (comp.getExecutionType()) {
        case ACTIVITY:
          activityExecutor.execute(comp.getCompProc(), Void.class, comp.getCompArg());
          break;
        case CHILD_WORKFLOW:
          CompensationWorkflow compensationWfExecutor =
              Workflow.newChildWorkflowStub(
                  CompensationWorkflow.class,
                  new ChildWorkflowOptions.Builder().setTaskList(comp.getTaskList()).build());
          compensationWfExecutor.compensate((List<CompensationItem>) comp.getCompArg());
          break;
        default:
          System.out.printf("Unhandled execution type: %s\n", comp.getExecutionType());
          break;
      }
    }
  }

  public enum ExecutionType {
    ACTIVITY,
    CHILD_WORKFLOW
  }

  private String getExecutionName(Object compensationProc) {
    // Better use reflection here?
    SerializedLambda lambda = LambdaUtils.toSerializedLambda(compensationProc);
    String className =
        Arrays.stream(lambda.getImplClass().split("/")).reduce((first, second) -> second).get();
    // handle internal classes
    className = Arrays.stream(className.split("\\$")).reduce((first, second) -> second).get();

    String methodName = lambda.getImplMethodName();
    // does not takes into account activity name at annotation, taken from InternalUtils
    return className + "::" + methodName;
  }

  private String getChildWfTaskList(Object compensationProc) {
    try {
      // A hack to extract task queue for a compensation
      SerializedLambda lambda = LambdaUtils.toSerializedLambda(compensationProc);
      Object proxy = lambda.getCapturedArg(0);
      InvocationHandler handler = Proxy.getInvocationHandler(proxy);
      Field stubField = handler.getClass().getDeclaredField("stub");
      stubField.setAccessible(true);
      ChildWorkflowStub target = (ChildWorkflowStub) stubField.get(handler);
      return target.getOptions().getTaskList();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public static class CompensationItem {
    private String compProc;
    private ExecutionType executionType;
    private Object compArg;
    private String taskList;

    public CompensationItem(
        String compProc, ExecutionType executionType, Object compArg, String taskList) {
      this.compProc = compProc;
      this.executionType = executionType;
      this.compArg = compArg;
      this.taskList = taskList;
    }

    public String getCompProc() {
      return compProc;
    }

    public void setCompProc(String compProc) {
      this.compProc = compProc;
    }

    public ExecutionType getExecutionType() {
      return executionType;
    }

    public void setExecutionType(ExecutionType executionType) {
      this.executionType = executionType;
    }

    public Object getCompArg() {
      return compArg;
    }

    public void setCompArg(Object compArg) {
      this.compArg = compArg;
    }

    public String getTaskList() {
      return taskList;
    }

    public void setTaskList(String taskList) {
      this.taskList = taskList;
    }
  }

  public static class Result<TResult, TCompensationArgs> {
    private TResult result;
    private TCompensationArgs compensationArgs;

    public Result(TResult result, TCompensationArgs compensationArgs) {
      this.result = result;
      this.compensationArgs = compensationArgs;
    }

    public TResult getResult() {
      return result;
    }

    public void setResult(TResult result) {
      this.result = result;
    }

    public TCompensationArgs getCompensationArgs() {
      return compensationArgs;
    }

    public void setCompensationArgs(TCompensationArgs compensationArgs) {
      this.compensationArgs = compensationArgs;
    }
  }

  public static class WorkflowResult<TResult> {
    private TResult result;
    private List<CompensationItem> compensationArgs;

    public WorkflowResult(TResult result, List<CompensationItem> compensationArgs) {
      this.result = result;
      this.compensationArgs = compensationArgs;
    }

    public TResult getResult() {
      return result;
    }

    public void setResult(TResult result) {
      this.result = result;
    }

    public List<CompensationItem> getCompensationArgs() {
      return compensationArgs;
    }

    public void setCompensationArgs(List<CompensationItem> compensationArgs) {
      this.compensationArgs = compensationArgs;
    }
  }

  public static class Compensation<TCompensationArgs> {
    private TCompensationArgs compensationArgs;
  }
}
