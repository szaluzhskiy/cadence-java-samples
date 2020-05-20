package com.uber.cadence.samples.hello.spring;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

import com.uber.cadence.client.ActivityCompletionClient;
import com.uber.cadence.client.WorkflowClient;
import javax.annotation.PostConstruct;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class NewChildRestController {

  private ActivityCompletionClient completionClient;

  @PostConstruct
  void init() {
    WorkflowClient workflowClient = WorkflowClient.newInstance("127.0.0.1", 7933, DOMAIN);
    completionClient = workflowClient.newActivityCompletionClient();
  }

  @PutMapping(value = "/result")
  @ResponseBody
  public ResponseEntity<byte[]> handle(@RequestBody byte[] taskToken) {
    if (completionClient != null) {
      String response = "This is the response";
      completionClient.complete(taskToken, "THIS IS THE END!");
      System.out.println("Complete activity!");
      return ResponseEntity.ok().body(response.getBytes());
    }

    return ResponseEntity.notFound().build();
  }

  @GetMapping("/test")
  public String check() {
    return "REST API is available";
  }
}
