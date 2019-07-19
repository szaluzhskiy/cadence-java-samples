package com.uber.cadence.samples.hello.extandabletypeadapter;

public class GreetingBaseArgs {
  private String name;

  public GreetingBaseArgs() {

  }

  public GreetingBaseArgs(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
