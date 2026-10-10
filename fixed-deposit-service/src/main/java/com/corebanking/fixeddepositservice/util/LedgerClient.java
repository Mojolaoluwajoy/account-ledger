package com.corebanking.fixeddepositservice.util;

import com.corebanking.fixeddepositservice.entity.LedgerPosting;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class LedgerClient {

  private final RestTemplate restTemplate;

  @Value("${ledger.service.url:http://localhost:8084}")
  private String ledgerServiceUrl;

  /*
   * Sends one posting to the ledger service. Throws if the ledger service
   * rejects it or cannot be reached, so the caller knows to try again.
   */
  public void post(LedgerPosting posting) {
    Map<String, Object> request = new HashMap<>();
    request.put("transactionType", posting.getTransactionType());
    request.put("amount", posting.getAmount());
    request.put("transactionReference", posting.getTransactionReference());
    request.put("description", posting.getDescription());

    restTemplate.postForObject(ledgerServiceUrl + "/api/v1/ledger/entries", request, Object.class);
  }
}
