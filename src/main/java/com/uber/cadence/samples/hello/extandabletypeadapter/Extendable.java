package com.uber.cadence.samples.hello.extandabletypeadapter;

import java.util.Map;

public interface Extendable {

  Map<String, Object> getExtension();

  void setExtension(Map<String, Object> extension);
}
