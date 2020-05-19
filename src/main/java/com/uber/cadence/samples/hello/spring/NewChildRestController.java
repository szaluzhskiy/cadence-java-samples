package com.uber.cadence.samples.hello.spring;

import com.uber.cadence.client.ActivityCompletionClient;
import com.uber.cadence.client.WorkflowClient;
import org.apache.commons.codec.binary.Hex;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

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
      System.out.println("st===1");
      String response = "This is the response";
      System.out.println(Hex.encodeHexString(taskToken));
      System.out.println("st===4");
      completionClient.complete(taskToken, "THIS IS THE END!");
      System.out.println("st===5");

      return ResponseEntity.ok().body(response.getBytes());
    }

    return ResponseEntity.notFound().build();
  }

  @GetMapping("/test")
  public String check() {
    return "REST API is available";
  }
}
