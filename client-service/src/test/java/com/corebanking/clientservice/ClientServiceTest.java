package com.corebanking.clientservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.corebanking.clientservice.dto.ClientResponse;
import com.corebanking.clientservice.dto.CreateClientRequest;
import com.corebanking.clientservice.dto.FineractClientResponse;
import com.corebanking.clientservice.dto.FineractCreateClientRequest;
import com.corebanking.clientservice.entity.Client;
import com.corebanking.clientservice.entity.ClientStatus;
import com.corebanking.clientservice.exception.ClientNotFoundException;
import com.corebanking.clientservice.exception.DuplicateEmailException;
import com.corebanking.clientservice.feign.FineractClient;
import com.corebanking.clientservice.repository.ClientRepository;
import com.corebanking.clientservice.service.ClientService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
public class ClientServiceTest {

  @Mock private ClientRepository clientRepository;

  @Mock private FineractClient fineractClient;

  @InjectMocks private ClientService clientService;

  private CreateClientRequest validRequest;
  private Client savedClient;

  @BeforeEach
  void setUp() {
    validRequest = new CreateClientRequest();
    validRequest.setFirstName("Jane");
    validRequest.setLastName("Doe");
    validRequest.setEmail("janedoe@gmail.com");
    validRequest.setPhoneNumber("08142988584");
    validRequest.setDateOfBirth(LocalDate.of(1990, 1, 1));

    savedClient = new Client();
    savedClient.setId(1L);
    savedClient.setFirstName("Jane");
    savedClient.setLastName("Doe");
    savedClient.setEmail("janedoe@gmail.com");
    savedClient.setPhoneNumber("08142988584");
    savedClient.setDateOfBirth(LocalDate.of(1990, 1, 1));
  }

  @Test
  void createClient_withValidRequest_shouldReturnClientResponse() {
    when(clientRepository.existsByEmail(validRequest.getEmail())).thenReturn(false);
    when(clientRepository.save(any(Client.class))).thenReturn(savedClient);
    FineractClientResponse fineractResponse = new FineractClientResponse();
    fineractResponse.setClientId(42L);
    when(fineractClient.createClient(any(FineractCreateClientRequest.class)))
        .thenReturn(fineractResponse);

    ClientResponse response = clientService.createClient(validRequest);

    assertNotNull(response);
    assertEquals("Jane", response.getFirstName());
    assertEquals("Doe", response.getLastName());
    assertEquals("janedoe@gmail.com", response.getEmail());
    assertEquals(42L, response.getFineractClientId());
    assertEquals(ClientStatus.ACTIVE, response.getStatus());
    verify(clientRepository, times(2)).save(any(Client.class));
  }

  @Test
  void createClient_whenFineractFails_shouldReturnPendingClient() {
    when(clientRepository.existsByEmail(validRequest.getEmail())).thenReturn(false);
    when(clientRepository.save(any(Client.class))).thenReturn(savedClient);
    when(fineractClient.createClient(any(FineractCreateClientRequest.class)))
        .thenThrow(new RuntimeException("Fineract unavailable"));

    ClientResponse response = clientService.createClient(validRequest);

    assertEquals(ClientStatus.PENDING, response.getStatus());
    verify(clientRepository, times(1)).save(any(Client.class));
  }

  @Test
  void createClient_withDuplicateEmail_shouldThrowException() {
    when(clientRepository.existsByEmail(validRequest.getEmail())).thenReturn(true);

    assertThrows(
        DuplicateEmailException.class,
        () -> {
          clientService.createClient(validRequest);
        });

    verify(clientRepository, never()).save(any(Client.class));
  }

  @Test
  void createClient_whenEmailInsertedConcurrently_shouldThrowDuplicateEmailException() {
    when(clientRepository.existsByEmail(validRequest.getEmail())).thenReturn(false);
    when(clientRepository.save(any(Client.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate key"));

    assertThrows(DuplicateEmailException.class, () -> clientService.createClient(validRequest));

    verify(fineractClient, never()).createClient(any(FineractCreateClientRequest.class));
  }

  @Test
  void retryPendingFineractSync_shouldActivatePendingClients() {
    savedClient.setStatus(ClientStatus.PENDING);
    FineractClientResponse fineractResponse = new FineractClientResponse();
    fineractResponse.setClientId(42L);
    when(clientRepository.findByStatusAndFineractClientIdIsNull(ClientStatus.PENDING))
        .thenReturn(List.of(savedClient));
    when(fineractClient.createClient(any(FineractCreateClientRequest.class)))
        .thenReturn(fineractResponse);

    clientService.retryPendingFineractSync();

    assertEquals(ClientStatus.ACTIVE, savedClient.getStatus());
    assertEquals(42L, savedClient.getFineractClientId());
    verify(clientRepository, times(1)).save(savedClient);
  }

  @Test
  void getClientById_withInvalidId_shouldThrowException() {
    when(clientRepository.findById(99L)).thenReturn(Optional.empty());

    assertThrows(
        ClientNotFoundException.class,
        () -> {
          clientService.getClientById(99L);
        });
  }
}
