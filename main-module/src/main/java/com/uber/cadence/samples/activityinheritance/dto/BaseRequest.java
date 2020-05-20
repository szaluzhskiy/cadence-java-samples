package com.uber.cadence.samples.activityinheritance.dto;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public abstract class BaseRequest {

  private final String requestId;
}
