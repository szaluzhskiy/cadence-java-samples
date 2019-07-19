package com.uber.cadence.samples.hello.extandabletypeadapter;

public class GreetingChildArgs extends GreetingBaseArgs {
  private String otherName;

  public GreetingChildArgs(String name, String otherName) {
    super(name);
    this.otherName = otherName;
  }

  public String getOtherName() {
    return otherName;
  }

  public void setOtherName(String otherName) {
    this.otherName = otherName;
  }
}
