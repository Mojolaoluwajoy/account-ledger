package com.corebanking.fixeddepositservice.util;

import com.corebanking.fixeddepositservice.exception.ClientNotFoundException;
import com.corebanking.fixeddepositservice.exception.ClientServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientServiceClient {

  private final RestTemplate restTemplate;

  @Value("${client.service.url:http://localhost:8081}")
  private String clientServiceUrl;

  public void requireClientExists(Long clientId) {
    try {
      restTemplate.getForObject(clientServiceUrl + "/api/v1/clients/" + clientId, Object.class);
    } catch (HttpClientErrorException.NotFound e) {
      throw new ClientNotFoundException("Client not found with ID: " + clientId);
    } catch (RestClientException e) {
      log.error("Could not verify client {} with client service", clientId, e);
      throw new ClientServiceUnavailableException(
          "Could not verify client " + clientId + " right now. Please try again shortly.");
    }
  }
}
