package com.accountledger.fixeddepositservice.util;

import com.accountledger.fixeddepositservice.entity.LedgerPosting;
import com.accountledger.fixeddepositservice.feign.LedgerServiceApi;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LedgerClient {

  private final LedgerServiceApi ledgerServiceApi;

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

    ledgerServiceApi.postEntry(request);
  }
}
