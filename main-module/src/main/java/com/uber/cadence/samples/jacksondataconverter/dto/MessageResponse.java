package com.uber.cadence.samples.jacksondataconverter.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import java.util.Map;

@JsonDeserialize(as = MessageResponseImpl.class)
public interface MessageResponse {
  @JsonAnyGetter
  Map<String, Object> getAdditionalProperties();

  @JsonAnySetter
  void setAdditionalProperties(String key, Object value);

  @JsonProperty("requestId")
  String getRequestId();

  @JsonProperty("requestId")
  void setRequestId(String requestId);

  @JsonProperty("error")
  ErrorBody getError();

  @JsonProperty("error")
  void setError(ErrorBody error);

  @JsonProperty("errorList")
  List<ErrorBody> getErrorList();

  @JsonProperty("errorList")
  void setErrorList(List<ErrorBody> errorList);
}
