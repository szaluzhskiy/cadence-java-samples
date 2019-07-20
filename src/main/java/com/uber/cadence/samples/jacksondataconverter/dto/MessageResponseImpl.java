package com.uber.cadence.samples.jacksondataconverter.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"requestId", "error", "errorList"})
public class MessageResponseImpl implements MessageResponse {

  @JsonProperty("requestId")
  private String requestId;

  @JsonProperty("error")
  private ErrorBody error;

  @JsonProperty("errorList")
  private List<ErrorBody> errorList;

  @JsonIgnore
  private Map<String, Object> additionalProperties = new HashMap<String, Object>();

  @JsonProperty("requestId")
  public String getRequestId() {
    return this.requestId;
  }

  @JsonProperty("requestId")
  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  @JsonProperty("error")
  public ErrorBody getError() {
    return this.error;
  }

  @JsonProperty("error")
  public void setError(ErrorBody error) {
    this.error = error;
  }

  @JsonProperty("errorList")
  public List<ErrorBody> getErrorList() {
    return this.errorList;
  }

  @JsonProperty("errorList")
  public void setErrorList(List<ErrorBody> errorList) {
    this.errorList = errorList;
  }

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return additionalProperties;
  }

  @JsonAnySetter
  public void setAdditionalProperties(String key, Object value) {
    this.additionalProperties.put(key, value);
  }
}
