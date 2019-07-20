package com.uber.cadence.samples.activityinheritance.dto;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TypeARequest extends BaseRequest {

  private final String requestA;

  @Builder
  public TypeARequest(String requestId, String requestA) {
    super(requestId);
    this.requestA = requestA;
  }
}
