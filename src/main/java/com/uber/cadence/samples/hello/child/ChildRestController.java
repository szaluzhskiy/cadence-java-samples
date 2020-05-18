package com.uber.cadence.samples.hello.child;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.uber.cadence.client.ActivityCompletionClient;
import com.uber.cadence.client.WorkflowClient;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import org.apache.commons.codec.binary.Hex;

public class ChildRestController {
  public static ActivityCompletionClient completionClient;

  public static void main(String[] args) throws Exception {
    WorkflowClient workflowClient = WorkflowClient.newInstance("127.0.0.1", 7933, DOMAIN);
    ChildRestController.completionClient = workflowClient.newActivityCompletionClient();
    HttpServer server = HttpServer.create(new InetSocketAddress(8092), 0);
    server.createContext("/result", new MyHandler());
    server.setExecutor(null); // creates a default executor
    server.start();
  }

  static class MyHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange t) throws IOException {
      System.out.println("st===1");
      String response = "This is the response";
      t.sendResponseHeaders(200, response.length());
      InputStream is = t.getRequestBody();
      byte[] taskToken = readTaskToken(is);
      System.out.println(Hex.encodeHexString(taskToken));
      System.out.println("st===4");
      completionClient.complete(taskToken, "THIS IS THE END!");
      System.out.println("st===5");
    }
  }

  private static byte[] readTaskToken(InputStream in) throws IOException {
    System.out.println("st===2");
    byte[] buffer = new byte[1024];
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    int read = 0;
    while ((read = in.read(buffer)) != -1) {
      baos.write(buffer, 0, read);
    }
    System.out.println("st===3");
    return baos.toByteArray();
  }
}
