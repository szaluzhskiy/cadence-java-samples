package com.uber.cadence.samples.activityinheritance.dto;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TypeBRequest extends BaseRequest {

  private final String requestB;

  @Builder
  public TypeBRequest(String requestId, String requestB) {
    super(requestId);
    this.requestB = requestB;
  }
}
