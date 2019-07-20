package com.uber.cadence.samples.jacksondataconverter.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import java.util.Map;

@JsonDeserialize(as = ErrorBodyImpl.class)
public interface ErrorBody {
  @JsonAnyGetter
  Map<String, Object> getAdditionalProperties();

  @JsonAnySetter
  void setAdditionalProperties(String key, Object value);

  @JsonProperty("code")
  String getCode();

  @JsonProperty("code")
  void setCode(String code);

  @JsonProperty("description")
  String getDescription();

  @JsonProperty("description")
  void setDescription(String description);

  @JsonProperty("messages")
  List<String> getMessages();

  @JsonProperty("messages")
  void setMessages(List<String> messages);
}
