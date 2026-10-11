package com.corebanking.fixeddepositservice.util;

import com.corebanking.fixeddepositservice.exception.ClientNotFoundException;
import com.corebanking.fixeddepositservice.exception.ClientServiceUnavailableException;
import com.corebanking.fixeddepositservice.feign.ClientServiceApi;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientServiceClient {

  private final ClientServiceApi clientServiceApi;

  public void requireClientExists(Long clientId) {
    try {
      clientServiceApi.getClientById(clientId);
    } catch (FeignException.NotFound e) {
      throw new ClientNotFoundException("Client not found with ID: " + clientId);
    } catch (FeignException e) {
      log.error("Could not verify client {} with client service", clientId, e);
      throw new ClientServiceUnavailableException(
          "Could not verify client " + clientId + " right now. Please try again shortly.");
    }
  }
}
