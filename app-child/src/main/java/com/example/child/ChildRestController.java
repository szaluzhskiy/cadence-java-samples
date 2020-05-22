package com.example.child;

import com.uber.cadence.client.ActivityCompletionClient;
import com.uber.cadence.client.WorkflowClient;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.example.child.SampleConstants.CADENCE_HOST;
import static com.example.child.SampleConstants.CADENCE_PORT;

@RestController
@Slf4j
public class ChildRestController {

  private ActivityCompletionClient completionClient;

  @PostConstruct
  void init() {
    WorkflowClient workflowClient =
        WorkflowClient.newInstance(System.getenv(CADENCE_HOST), Integer.parseInt(System.getenv(CADENCE_PORT)),
                SampleConstants.DOMAIN);
    completionClient = workflowClient.newActivityCompletionClient();
  }

  @PutMapping(value = "/result")
  @ResponseBody
  public ResponseEntity<byte[]> handle(@RequestBody byte[] taskToken) {
    if (completionClient != null) {
      String response = "This is the response";
      completionClient.complete(taskToken, "THIS IS THE END!");
      log.info("Complete activity!");

      return ResponseEntity.ok().body(response.getBytes());
    }

    return ResponseEntity.notFound().build();
  }

  @GetMapping("/test")
  public String check() {
    return "REST API is available";
  }
}
