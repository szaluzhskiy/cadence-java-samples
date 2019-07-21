package com.uber.cadence.samples.hello.child.child;

/**
 * The child workflow implementation. A workflow implementation must always be public for the
 * Cadence library to be able to create instances.
 */
public class GreetingChildSubInterfaceImpl implements GreetingChild {
  public String composeGreeting(String greeting, String name) {
    return greeting + " " + name + "!";
  }
}
