package com.accountledger.clientservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.accountledger.clientservice.dto.ClientResponse;
import com.accountledger.clientservice.dto.CreateClientRequest;
import com.accountledger.clientservice.dto.FineractClientResponse;
import com.accountledger.clientservice.dto.FineractClientSearchResponse;
import com.accountledger.clientservice.dto.FineractCreateClientRequest;
import com.accountledger.clientservice.dto.FineractUpdateClientRequest;
import com.accountledger.clientservice.entity.Client;
import com.accountledger.clientservice.entity.ClientStatus;
import com.accountledger.clientservice.exception.ClientChangedException;
import com.accountledger.clientservice.exception.ClientNotFoundException;
import com.accountledger.clientservice.exception.DuplicateEmailException;
import com.accountledger.clientservice.exception.DuplicatePhoneNumberException;
import com.accountledger.clientservice.feign.FineractClient;
import com.accountledger.clientservice.repository.ClientRepository;
import com.accountledger.clientservice.service.ClientService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

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
    assertEquals("Fineract unavailable", response.getFineractSyncError());
    // Saved once on creation and once more to record why Fineract failed.
    verify(clientRepository, times(2)).save(any(Client.class));
  }

  @Test
  void createClient_withDuplicatePhoneNumber_shouldThrowException() {
    when(clientRepository.existsByEmail(validRequest.getEmail())).thenReturn(false);
    when(clientRepository.existsByPhoneNumber(validRequest.getPhoneNumber())).thenReturn(true);

    assertThrows(
        DuplicatePhoneNumberException.class, () -> clientService.createClient(validRequest));

    verify(clientRepository, never()).save(any(Client.class));
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
  void createClient_withDuplicateEmailInDifferentCase_shouldThrowException() {
    validRequest.setEmail(" JaneDoe@Gmail.COM ");
    when(clientRepository.existsByEmail("janedoe@gmail.com")).thenReturn(true);

    assertThrows(DuplicateEmailException.class, () -> clientService.createClient(validRequest));

    verify(clientRepository, never()).save(any(Client.class));
  }

  @Test
  void createClient_shouldStoreEmailLowerCased() {
    validRequest.setEmail("JaneDoe@Gmail.com");
    when(clientRepository.existsByEmail("janedoe@gmail.com")).thenReturn(false);
    when(clientRepository.existsByPhoneNumber(validRequest.getPhoneNumber())).thenReturn(false);
    when(clientRepository.save(any(Client.class))).thenReturn(savedClient);

    clientService.createClient(validRequest);

    ArgumentCaptor<Client> stored = ArgumentCaptor.forClass(Client.class);
    verify(clientRepository, atLeastOnce()).save(stored.capture());
    assertEquals("janedoe@gmail.com", stored.getAllValues().get(0).getEmail());
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

  @Test
  void createClient_shouldSendExternalIdAndEmailToFineract() {
    when(clientRepository.existsByEmail(validRequest.getEmail())).thenReturn(false);
    when(clientRepository.save(any(Client.class))).thenReturn(savedClient);
    when(fineractClient.createClient(any(FineractCreateClientRequest.class)))
        .thenReturn(fineractResponse(42L));

    clientService.createClient(validRequest);

    ArgumentCaptor<FineractCreateClientRequest> sent =
        ArgumentCaptor.forClass(FineractCreateClientRequest.class);
    verify(fineractClient).createClient(sent.capture());
    assertEquals("CB-1", sent.getValue().getExternalId());
    assertEquals("janedoe@gmail.com", sent.getValue().getEmailAddress());
    // A first attempt cannot already exist in Fineract, so no lookup is made.
    verify(fineractClient, never()).findByExternalId(any());
  }

  @Test
  void retryPendingFineractSync_whenClientAlreadyInFineract_shouldAdoptItInsteadOfCreating() {
    savedClient.setStatus(ClientStatus.PENDING);
    when(clientRepository.findByStatusAndFineractClientIdIsNull(ClientStatus.PENDING))
        .thenReturn(List.of(savedClient));
    when(fineractClient.findByExternalId("CB-1")).thenReturn(searchResult(77L, "Jane", "Doe"));

    clientService.retryPendingFineractSync();

    assertEquals(77L, savedClient.getFineractClientId());
    assertEquals(ClientStatus.ACTIVE, savedClient.getStatus());
    verify(fineractClient, never()).createClient(any(FineractCreateClientRequest.class));
  }

  @Test
  void retryPendingFineractSync_whenExternalIdBelongsToSomeoneElse_shouldLeaveClientPending() {
    savedClient.setStatus(ClientStatus.PENDING);
    when(clientRepository.findByStatusAndFineractClientIdIsNull(ClientStatus.PENDING))
        .thenReturn(List.of(savedClient));
    when(fineractClient.findByExternalId("CB-1")).thenReturn(searchResult(77L, "Someone", "Else"));

    clientService.retryPendingFineractSync();

    assertNull(savedClient.getFineractClientId());
    assertEquals(ClientStatus.PENDING, savedClient.getStatus());
    assertNotNull(savedClient.getFineractSyncError());
    verify(fineractClient, never()).createClient(any(FineractCreateClientRequest.class));
  }

  @Test
  void updateClient_linkedToFineract_shouldPushChangesAndClearPendingFlag() {
    savedClient.setFineractClientId(42L);
    savedClient.setStatus(ClientStatus.ACTIVE);
    validRequest.setFirstName("Janet");
    validRequest.setEmail("janet@gmail.com");
    when(clientRepository.findById(1L)).thenReturn(Optional.of(savedClient));
    when(clientRepository.existsByEmail("janet@gmail.com")).thenReturn(false);
    when(clientRepository.save(any(Client.class))).thenReturn(savedClient);

    ClientResponse response = clientService.updateClient(1L, validRequest, null);

    ArgumentCaptor<FineractUpdateClientRequest> sent =
        ArgumentCaptor.forClass(FineractUpdateClientRequest.class);
    verify(fineractClient).updateClient(eq(42L), sent.capture());
    assertEquals("Janet", sent.getValue().getFirstname());
    assertEquals("janet@gmail.com", sent.getValue().getEmailAddress());
    assertEquals("janet@gmail.com", response.getEmail());
    assertFalse(response.isFineractSyncPending());
  }

  @Test
  void updateClient_whenFineractFails_shouldKeepLocalChangeAndStayPending() {
    savedClient.setFineractClientId(42L);
    savedClient.setStatus(ClientStatus.ACTIVE);
    validRequest.setFirstName("Janet");
    when(clientRepository.findById(1L)).thenReturn(Optional.of(savedClient));
    when(clientRepository.save(any(Client.class))).thenReturn(savedClient);
    when(fineractClient.updateClient(eq(42L), any(FineractUpdateClientRequest.class)))
        .thenThrow(new RuntimeException("Fineract unavailable"));

    ClientResponse response = clientService.updateClient(1L, validRequest, null);

    assertEquals("Janet", response.getFirstName());
    assertTrue(response.isFineractSyncPending());
    assertEquals("Fineract unavailable", response.getFineractSyncError());
  }

  @Test
  void updateClient_notYetInFineract_shouldNotCallFineract() {
    savedClient.setStatus(ClientStatus.PENDING);
    when(clientRepository.findById(1L)).thenReturn(Optional.of(savedClient));
    when(clientRepository.save(any(Client.class))).thenReturn(savedClient);

    ClientResponse response = clientService.updateClient(1L, validRequest, null);

    assertFalse(response.isFineractSyncPending());
    verify(fineractClient, never()).updateClient(any(), any(FineractUpdateClientRequest.class));
  }

  @Test
  void updateClient_toEmailOfAnotherClient_shouldThrowException() {
    validRequest.setEmail("taken@gmail.com");
    when(clientRepository.findById(1L)).thenReturn(Optional.of(savedClient));
    when(clientRepository.existsByEmail("taken@gmail.com")).thenReturn(true);

    assertThrows(
        DuplicateEmailException.class, () -> clientService.updateClient(1L, validRequest, null));

    verify(clientRepository, never()).save(any(Client.class));
  }

  @Test
  void retryPendingFineractSync_shouldPushPendingUpdates() {
    savedClient.setFineractClientId(42L);
    savedClient.setStatus(ClientStatus.ACTIVE);
    savedClient.setFineractSyncPending(true);
    when(clientRepository.findByFineractSyncPendingTrueAndFineractClientIdIsNotNull())
        .thenReturn(List.of(savedClient));

    clientService.retryPendingFineractSync();

    verify(fineractClient).updateClient(eq(42L), any(FineractUpdateClientRequest.class));
    assertFalse(savedClient.isFineractSyncPending());
    verify(clientRepository).save(savedClient);
  }

  private FineractClientResponse fineractResponse(Long clientId) {
    FineractClientResponse response = new FineractClientResponse();
    response.setClientId(clientId);
    return response;
  }

  private FineractClientSearchResponse searchResult(Long id, String firstname, String lastname) {
    FineractClientSearchResponse.Item item = new FineractClientSearchResponse.Item();
    item.setId(id);
    item.setFirstname(firstname);
    item.setLastname(lastname);
    FineractClientSearchResponse response = new FineractClientSearchResponse();
    response.setPageItems(List.of(item));
    return response;
  }

  @Test
  void updateClient_basedOnOutdatedVersion_shouldThrowClientChangedAndSaveNothing() {
    savedClient.setVersion(3L);
    when(clientRepository.findById(1L)).thenReturn(Optional.of(savedClient));

    assertThrows(
        ClientChangedException.class, () -> clientService.updateClient(1L, validRequest, 2L));

    verify(clientRepository, never()).save(any(Client.class));
  }

  @Test
  void updateClient_basedOnCurrentVersion_shouldSave() {
    savedClient.setVersion(3L);
    when(clientRepository.findById(1L)).thenReturn(Optional.of(savedClient));
    when(clientRepository.save(any(Client.class))).thenReturn(savedClient);

    clientService.updateClient(1L, validRequest, 3L);

    verify(clientRepository).save(savedClient);
  }

  @Test
  void retryPendingFineractSync_whenClientChangedMeanwhile_shouldCarryOnWithOtherClients() {
    Client changedMeanwhile = new Client();
    changedMeanwhile.setId(2L);
    changedMeanwhile.setFirstName("Ade");
    changedMeanwhile.setLastName("Ola");
    changedMeanwhile.setEmail("ade@gmail.com");
    changedMeanwhile.setPhoneNumber("08000000002");
    savedClient.setStatus(ClientStatus.PENDING);
    FineractClientResponse fineractResponse = new FineractClientResponse();
    fineractResponse.setClientId(42L);
    when(clientRepository.findByStatusAndFineractClientIdIsNull(ClientStatus.PENDING))
        .thenReturn(List.of(changedMeanwhile, savedClient));
    when(fineractClient.createClient(any(FineractCreateClientRequest.class)))
        .thenReturn(fineractResponse);
    when(clientRepository.save(changedMeanwhile))
        .thenThrow(new ObjectOptimisticLockingFailureException(Client.class, 2L));

    clientService.retryPendingFineractSync();

    assertEquals(ClientStatus.ACTIVE, savedClient.getStatus());
    verify(clientRepository).save(savedClient);
  }
}
