package com.accountledger.clientservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/*
 * Periodically retries clients that could not be created in Fineract.
 * Switch off with fineract.sync.retry-enabled=false.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fineract.sync.retry-enabled", havingValue = "true")
public class FineractSyncScheduler {

  private final ClientService clientService;

  @Scheduled(
      initialDelayString = "${fineract.sync.retry-delay-ms:60000}",
      fixedDelayString = "${fineract.sync.retry-delay-ms:60000}")
  public void retryPendingClients() {
    clientService.retryPendingFineractSync();
  }
}
