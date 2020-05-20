package com.uber.cadence.samples.hellotimer;

public class CallbackServiceImpl {
  public CallbackServiceImpl() {}

  public String success(String result) {
    return result;
  }

  public String fail(Throwable ex) {
    System.out.println(ex);
    return "FAILED";
  }
}
