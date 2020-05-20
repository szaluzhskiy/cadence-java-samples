package com.uber.cadence.samples.activityinheritance;

import com.uber.cadence.workflow.WorkflowMethod;
import java.util.List;

public interface ActivityInheritanceWorkflow {

  @WorkflowMethod
  List<String> send(String request);
}
