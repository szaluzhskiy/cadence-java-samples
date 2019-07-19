package com.uber.cadence.samples.hello.extandabletypeadapter;

import java.util.HashMap;
import java.util.Map;

public class GreetingBaseArgsExtended extends GreetingBaseArgs implements Extendable {

  private Map<String,Object> extension = new HashMap<>();

  public GreetingBaseArgsExtended(String name) {
    super(name);
  }

  public Map<String, Object> getExtension() {
    return extension;
  }

  public void setExtension(Map<String, Object> extension) {
    this.extension = extension;
  }

}
