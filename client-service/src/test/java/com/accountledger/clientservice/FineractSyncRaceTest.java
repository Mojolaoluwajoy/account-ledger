package com.accountledger.clientservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.accountledger.clientservice.dto.FineractClientResponse;
import com.accountledger.clientservice.dto.FineractClientSearchResponse;
import com.accountledger.clientservice.dto.FineractCreateClientRequest;
import com.accountledger.clientservice.entity.Client;
import com.accountledger.clientservice.entity.ClientStatus;
import com.accountledger.clientservice.feign.FineractClient;
import com.accountledger.clientservice.repository.ClientRepository;
import com.accountledger.clientservice.service.ClientService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/*
 * The sync job must leave alone a client created moments ago: the request
 * that created it may still be waiting for Fineract, and creating it a
 * second time would leave a misleading sync error.
 */
@SpringBootTest
class FineractSyncRaceTest {

  @Autowired private ClientService clientService;
  @Autowired private ClientRepository clientRepository;

  @MockitoBean private FineractClient fineractClient;

  @AfterEach
  void tearDown() {
    clientRepository.deleteAll();
  }

  @Test
  void retry_shouldSkipClientCreatedMomentsAgo() {
    Long id = clientRepository.save(pendingClient(LocalDateTime.now())).getId();

    clientService.retryPendingFineractSync();

    verify(fineractClient, never()).findByExternalId(any());
    verify(fineractClient, never()).createClient(any());
    assertEquals(ClientStatus.PENDING, clientRepository.findById(id).get().getStatus());
  }

  @Test
  void retry_shouldCreateClientWaitingMoreThanAMinute() {
    Long id = clientRepository.save(pendingClient(LocalDateTime.now().minusMinutes(2))).getId();
    when(fineractClient.findByExternalId("CB-" + id))
        .thenReturn(new FineractClientSearchResponse());
    FineractClientResponse created = new FineractClientResponse();
    created.setClientId(42L);
    when(fineractClient.createClient(any(FineractCreateClientRequest.class))).thenReturn(created);

    clientService.retryPendingFineractSync();

    Client client = clientRepository.findById(id).get();
    assertEquals(ClientStatus.ACTIVE, client.getStatus());
    assertEquals(42L, client.getFineractClientId());
  }

  private static Client pendingClient(LocalDateTime createdAt) {
    Client client = new Client();
    client.setFirstName("Ada");
    client.setLastName("Obi");
    client.setEmail("ada@example.com");
    client.setPhoneNumber("08030000001");
    client.setDateOfBirth(LocalDate.of(1990, 4, 2));
    client.setStatus(ClientStatus.PENDING);
    client.setCreatedAt(createdAt);
    return client;
  }
}
