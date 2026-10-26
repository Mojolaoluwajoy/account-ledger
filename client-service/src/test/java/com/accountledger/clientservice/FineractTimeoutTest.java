package com.accountledger.clientservice;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accountledger.clientservice.feign.FineractClient;
import feign.RetryableException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/*
 * A Fineract that accepts the connection and never answers. The call must give
 * up after the configured read timeout instead of hanging.
 */
@SpringBootTest
class FineractTimeoutTest {

  private static final ServerSocket silentServer = openSilentServer();
  private static final List<Socket> accepted = new ArrayList<>();

  @Autowired private FineractClient fineractClient;

  @DynamicPropertySource
  static void fineractProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "fineract.base-url",
        () -> "http://localhost:" + silentServer.getLocalPort() + "/fineract-provider/api/v1");
    registry.add(
        "spring.cloud.openfeign.client.config.fineract-client-service.read-timeout", () -> "300");
  }

  @AfterAll
  static void closeServer() throws Exception {
    silentServer.close();
    for (Socket socket : accepted) {
      socket.close();
    }
  }

  @Test
  void slowFineract_shouldTimeOutInsteadOfHanging() {
    long start = System.nanoTime();

    assertThrows(RetryableException.class, () -> fineractClient.getClientById(1L));

    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
    assertTrue(elapsedMs < 5000, "took " + elapsedMs + " ms");
  }

  private static ServerSocket openSilentServer() {
    try {
      ServerSocket server = new ServerSocket(0);
      Thread acceptor =
          new Thread(
              () -> {
                while (!server.isClosed()) {
                  try {
                    accepted.add(server.accept());
                  } catch (Exception ignored) {
                    return;
                  }
                }
              });
      acceptor.setDaemon(true);
      acceptor.start();
      return server;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
