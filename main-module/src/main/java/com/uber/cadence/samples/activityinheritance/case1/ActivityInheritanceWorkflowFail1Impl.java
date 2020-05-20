package com.uber.cadence.samples.activityinheritance.case1;

import com.google.common.collect.Lists;
import com.uber.cadence.activity.ActivityOptions;
import com.uber.cadence.samples.activityinheritance.ActivityInheritanceWorkflow;
import com.uber.cadence.samples.activityinheritance.case1.ActivityInheritanceFail1.BaseActivity;
import com.uber.cadence.samples.activityinheritance.dto.TypeARequest;
import com.uber.cadence.samples.activityinheritance.dto.TypeAResponse;
import com.uber.cadence.samples.activityinheritance.dto.TypeBRequest;
import com.uber.cadence.samples.activityinheritance.dto.TypeBResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ActivityInheritanceWorkflowFail1Impl implements ActivityInheritanceWorkflow {

  private final BaseActivity<TypeARequest, TypeAResponse> activityA =
      com.uber.cadence.workflow.Workflow.newActivityStub(
          BaseActivity.class,
          new ActivityOptions.Builder().setScheduleToCloseTimeout(Duration.ofSeconds(10)).build());

  private final BaseActivity<TypeBRequest, TypeBResponse> activityB =
      com.uber.cadence.workflow.Workflow.newActivityStub(
          BaseActivity.class,
          new ActivityOptions.Builder().setScheduleToCloseTimeout(Duration.ofSeconds(10)).build());

  @Override
  public List<String> send(String request) {

    log.info("got request: {}", request);

    final List<String> result = Lists.newArrayList();

    final TypeARequest requestA =
        TypeARequest.builder().requestId(UUID.randomUUID().toString()).requestA(request).build();

    log.info("executing A with request: {} ...", requestA);
    result.add(activityA.process(requestA).getResponseA());

    final TypeBRequest requestB =
        TypeBRequest.builder().requestId(UUID.randomUUID().toString()).requestB(request).build();
    log.info("executing B with request: {} ...", requestB);
    result.add(activityB.process(requestB).getResponseB());

    log.info("returning from workflow: {} ...", result);
    return result;
  }
}
