package com.corebanking.savingsservice;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;

import com.corebanking.savingsservice.exception.ClientNotFoundException;
import com.corebanking.savingsservice.exception.ClientServiceUnavailableException;
import com.corebanking.savingsservice.feign.ClientServiceApi;
import com.corebanking.savingsservice.util.ClientServiceClient;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientServiceClientTest {

  private static final Request REQUEST =
      Request.create(
          Request.HttpMethod.GET,
          "http://localhost:8081/api/v1/clients/1",
          Map.of(),
          null,
          StandardCharsets.UTF_8,
          null);

  @Mock private ClientServiceApi clientServiceApi;

  @InjectMocks private ClientServiceClient clientServiceClient;

  @Test
  void requireClientExists_withExistingClient_shouldPass() {
    assertDoesNotThrow(() -> clientServiceClient.requireClientExists(1L));
  }

  @Test
  void requireClientExists_withUnknownClient_shouldThrowClientNotFound() {
    doThrow(new FeignException.NotFound("Not Found", REQUEST, null, Map.of()))
        .when(clientServiceApi)
        .getClientById(1L);

    assertThrows(ClientNotFoundException.class, () -> clientServiceClient.requireClientExists(1L));
  }

  @Test
  void requireClientExists_withServerError_shouldThrowUnavailable() {
    doThrow(new FeignException.InternalServerError("Server Error", REQUEST, null, Map.of()))
        .when(clientServiceApi)
        .getClientById(1L);

    assertThrows(
        ClientServiceUnavailableException.class, () -> clientServiceClient.requireClientExists(1L));
  }

  @Test
  void requireClientExists_withUnreachableService_shouldThrowUnavailable() {
    doThrow(
            new RetryableException(
                -1, "Connection refused", Request.HttpMethod.GET, (Long) null, REQUEST))
        .when(clientServiceApi)
        .getClientById(1L);

    assertThrows(
        ClientServiceUnavailableException.class, () -> clientServiceClient.requireClientExists(1L));
  }
}
