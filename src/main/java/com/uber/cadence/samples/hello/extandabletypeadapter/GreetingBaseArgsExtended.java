package com.uber.cadence.samples.hello.extandabletypeadapter;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.HashMap;
import java.util.Map;

public class GreetingBaseArgsExtended extends GreetingBaseArgs implements Extendable {

  public GreetingBaseArgsExtended() {
    super();
  }

  public GreetingBaseArgsExtended(String name) {
    super(name);
  }

  public GreetingBaseArgsExtended(String name, Map<String, Object> extension) {
    super(name);
    this.extension = extension;
  }

  private Map<String,Object> extension = new HashMap<>();

  public Map<String, Object> getExtension() {
    return extension;
  }

  public void setExtension(Map<String, Object> extension) {
    this.extension = extension;
  }

  @JsonAnyGetter
  public Map<String, Object> getUnknownFields() {
    return extension;
  }

  @JsonAnySetter
  public void setUnknownFields(String name, Object value) {
    extension.put(name, value);
  }
}
