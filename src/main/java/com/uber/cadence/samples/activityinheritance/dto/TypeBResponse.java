package com.uber.cadence.samples.activityinheritance.dto;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TypeBResponse extends BaseResponse {

  private final String responseB;

  @Builder
  public TypeBResponse(String requestId, boolean success, String responseB) {
    super(requestId, success);
    this.responseB = responseB;
  }

}
