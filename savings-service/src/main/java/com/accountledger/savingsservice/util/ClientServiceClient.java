package com.accountledger.savingsservice.util;

import com.accountledger.savingsservice.dto.ClientLookupResponse;
import com.accountledger.savingsservice.exception.ClientNotActiveException;
import com.accountledger.savingsservice.exception.ClientNotFoundException;
import com.accountledger.savingsservice.exception.ClientServiceUnavailableException;
import com.accountledger.savingsservice.feign.ClientServiceApi;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientServiceClient {

  private final ClientServiceApi clientServiceApi;

  private static final String ACTIVE = "ACTIVE";

  /*
   * Only an ACTIVE client may be given a new product. A client stays PENDING
   * until it has been created in Fineract.
   */
  public void requireActiveClient(Long clientId) {
    ClientLookupResponse response;
    try {
      response = clientServiceApi.getClientById(clientId);
    } catch (FeignException.NotFound e) {
      throw new ClientNotFoundException("Client not found with ID: " + clientId);
    } catch (FeignException e) {
      log.error("Could not verify client {} with client service", clientId, e);
      throw new ClientServiceUnavailableException(
          "Could not verify client " + clientId + " right now. Please try again shortly.");
    }

    String status =
        response == null || response.getData() == null ? null : response.getData().getStatus();
    if (status == null) {
      log.error("Client service answered for client {} without a status", clientId);
      throw new ClientServiceUnavailableException(
          "Could not verify client " + clientId + " right now. Please try again shortly.");
    }
    if (!ACTIVE.equals(status)) {
      throw new ClientNotActiveException(
          "Client " + clientId + " is not active. Current status: " + status);
    }
  }
}
