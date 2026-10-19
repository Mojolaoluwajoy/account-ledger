package com.accountledger.savingsservice;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.accountledger.savingsservice.dto.ClientLookupResponse;
import com.accountledger.savingsservice.exception.ClientNotActiveException;
import com.accountledger.savingsservice.exception.ClientNotFoundException;
import com.accountledger.savingsservice.exception.ClientServiceUnavailableException;
import com.accountledger.savingsservice.feign.ClientServiceApi;
import com.accountledger.savingsservice.util.ClientServiceClient;
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
  void requireActiveClient_withActiveClient_shouldPass() {
    when(clientServiceApi.getClientById(1L)).thenReturn(clientWithStatus("ACTIVE"));

    assertDoesNotThrow(() -> clientServiceClient.requireActiveClient(1L));
  }

  @Test
  void requireActiveClient_withPendingClient_shouldThrowClientNotActive() {
    when(clientServiceApi.getClientById(1L)).thenReturn(clientWithStatus("PENDING"));

    assertThrows(ClientNotActiveException.class, () -> clientServiceClient.requireActiveClient(1L));
  }

  @Test
  void requireActiveClient_withNoStatusInAnswer_shouldThrowUnavailable() {
    when(clientServiceApi.getClientById(1L)).thenReturn(new ClientLookupResponse());

    assertThrows(
        ClientServiceUnavailableException.class, () -> clientServiceClient.requireActiveClient(1L));
  }

  @Test
  void requireActiveClient_withUnknownClient_shouldThrowClientNotFound() {
    doThrow(new FeignException.NotFound("Not Found", REQUEST, null, Map.of()))
        .when(clientServiceApi)
        .getClientById(1L);

    assertThrows(ClientNotFoundException.class, () -> clientServiceClient.requireActiveClient(1L));
  }

  @Test
  void requireActiveClient_withServerError_shouldThrowUnavailable() {
    doThrow(new FeignException.InternalServerError("Server Error", REQUEST, null, Map.of()))
        .when(clientServiceApi)
        .getClientById(1L);

    assertThrows(
        ClientServiceUnavailableException.class, () -> clientServiceClient.requireActiveClient(1L));
  }

  @Test
  void requireActiveClient_withUnreachableService_shouldThrowUnavailable() {
    doThrow(
            new RetryableException(
                -1, "Connection refused", Request.HttpMethod.GET, (Long) null, REQUEST))
        .when(clientServiceApi)
        .getClientById(1L);

    assertThrows(
        ClientServiceUnavailableException.class, () -> clientServiceClient.requireActiveClient(1L));
  }

  private ClientLookupResponse clientWithStatus(String status) {
    ClientLookupResponse.Client client = new ClientLookupResponse.Client();
    client.setId(1L);
    client.setStatus(status);
    ClientLookupResponse response = new ClientLookupResponse();
    response.setData(client);
    return response;
  }
}
