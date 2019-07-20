package com.uber.cadence.samples.activityinheritance.dto;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TypeAResponse extends BaseResponse {

  @Builder
  public TypeAResponse(String requestId, boolean success, String responseA) {
    super(requestId, success);
    this.responseA = responseA;
  }

  private String responseA;
}
