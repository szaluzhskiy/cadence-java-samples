package com.uber.cadence.samples.activityinheritance.dto;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class BaseResponse {

  private final String requestId;
  private final boolean success;
}
