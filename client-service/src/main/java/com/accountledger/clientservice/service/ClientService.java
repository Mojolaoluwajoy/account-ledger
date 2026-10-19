package com.accountledger.clientservice.service;

import com.accountledger.clientservice.dto.ClientResponse;
import com.accountledger.clientservice.dto.CreateClientRequest;
import com.accountledger.clientservice.dto.FineractClientSearchResponse;
import com.accountledger.clientservice.dto.FineractCreateClientRequest;
import com.accountledger.clientservice.dto.FineractUpdateClientRequest;
import com.accountledger.clientservice.dto.PageResponse;
import com.accountledger.clientservice.entity.Client;
import com.accountledger.clientservice.entity.ClientStatus;
import com.accountledger.clientservice.exception.ClientChangedException;
import com.accountledger.clientservice.exception.ClientNotFoundException;
import com.accountledger.clientservice.exception.DuplicateEmailException;
import com.accountledger.clientservice.exception.DuplicatePhoneNumberException;
import com.accountledger.clientservice.feign.FineractClient;
import com.accountledger.clientservice.repository.ClientRepository;
import com.accountledger.clientservice.util.ClientMapper;
import com.accountledger.clientservice.util.Paging;
import feign.FeignException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientService {

  private static final int MAX_SYNC_ERROR_LENGTH = 500;
  private static final Pattern FINERACT_USER_MESSAGE =
      Pattern.compile("\"defaultUserMessage\"\\s*:\\s*\"([^\"]*)\"");
  private static final String FINERACT_DATE_PATTERN = "dd MMMM yyyy";
  private static final DateTimeFormatter FINERACT_DATE =
      DateTimeFormatter.ofPattern(FINERACT_DATE_PATTERN, Locale.ENGLISH);

  private final ClientRepository clientRepository;
  private final FineractClient fineractClient;

  /*
   * Our client ID with this prefix is sent to Fineract as the external ID. It
   * lets us recognise our own record there. Use a distinct prefix per
   * environment if several environments share one Fineract.
   */
  @Value("${fineract.external-id-prefix:CB-}")
  private String externalIdPrefix = "CB-";

  public ClientResponse createClient(CreateClientRequest request) {
    log.info("Creating client with email: {}", request.getEmail());

    if (clientRepository.existsByEmail(request.getEmail())) {
      throw new DuplicateEmailException(
          "Client with email " + request.getEmail() + " already exists");
    }
    if (clientRepository.existsByPhoneNumber(request.getPhoneNumber())) {
      throw new DuplicatePhoneNumberException(
          "Client with phone number " + request.getPhoneNumber() + " already exists");
    }

    Client client = ClientMapper.toEntity(request);
    client.setStatus(ClientStatus.PENDING);

    Client savedClient;
    try {
      savedClient = clientRepository.save(client);
    } catch (DataIntegrityViolationException e) {
      // Another request registered the same email or phone number between our
      // checks and the insert.
      throw duplicateFor(request);
    }
    log.info("Client saved to our database with ID: {}", savedClient.getId());

    createInFineract(savedClient, false);

    return ClientMapper.toResponse(savedClient);
  }

  /*
   * Retries everything that has not reached Fineract yet: clients that were
   * never created there (still PENDING with no Fineract ID) and clients whose
   * later changes were not pushed. Triggered by FineractSyncScheduler.
   */
  public void retryPendingFineractSync() {
    List<Client> notCreated =
        clientRepository.findByStatusAndFineractClientIdIsNull(ClientStatus.PENDING);
    List<Client> notUpdated =
        clientRepository.findByFineractSyncPendingTrueAndFineractClientIdIsNotNull();
    if (notCreated.isEmpty() && notUpdated.isEmpty()) {
      return;
    }

    log.info(
        "Retrying Fineract sync: {} client(s) to create, {} to update",
        notCreated.size(),
        notUpdated.size());
    notCreated.forEach(client -> createInFineract(client, true));
    notUpdated.forEach(this::pushUpdateToFineract);
  }

  /*
   * Creates the client in Fineract and marks it ACTIVE. If Fineract fails the
   * client stays PENDING so the retry job can pick it up later.
   *
   * On a retry the earlier attempt may have succeeded without us receiving the
   * answer, so we first look for a Fineract client carrying our external ID
   * and adopt it instead of creating a second one.
   */
  private void createInFineract(Client client, boolean isRetry) {
    String externalId = externalIdPrefix + client.getId();
    try {
      Long fineractClientId = isRetry ? findExistingFineractClient(client, externalId) : null;

      if (fineractClientId == null) {
        String activationDate = LocalDate.now().format(FINERACT_DATE);

        FineractCreateClientRequest fineractRequest =
            FineractCreateClientRequest.builder()
                .officeId(1)
                .legalFormId(1)
                .firstname(client.getFirstName())
                .lastname(client.getLastName())
                .active(true)
                .activationDate(activationDate)
                .dateFormat(FINERACT_DATE_PATTERN)
                .locale("en")
                .mobileNo(client.getPhoneNumber())
                .emailAddress(client.getEmail())
                .externalId(externalId)
                .build();

        fineractClientId = fineractClient.createClient(fineractRequest).getClientId();
      }

      client.setFineractClientId(fineractClientId);
      client.setStatus(ClientStatus.ACTIVE);
      client.setFineractSyncPending(false);
      client.setFineractSyncError(null);
      clientRepository.save(client);

      log.info("Client {} linked to Fineract client {}", client.getId(), fineractClientId);

    } catch (Exception e) {
      log.error("Failed to create client {} in Fineract, will retry later", client.getId(), e);
      recordSyncError(client, e);
    }
  }

  /*
   * Returns the ID of the Fineract client that already carries our external
   * ID, or null if there is none. A match with a different name is refused:
   * it means the external ID belongs to somebody else's record (for example
   * on a Fineract shared with another environment).
   */
  private Long findExistingFineractClient(Client client, String externalId) {
    FineractClientSearchResponse response = fineractClient.findByExternalId(externalId);
    if (response == null || response.getPageItems() == null || response.getPageItems().isEmpty()) {
      return null;
    }

    FineractClientSearchResponse.Item existing = response.getPageItems().get(0);
    boolean sameName =
        client.getFirstName().equalsIgnoreCase(existing.getFirstname())
            && client.getLastName().equalsIgnoreCase(existing.getLastname());
    if (!sameName) {
      throw new IllegalStateException(
          "Fineract client "
              + existing.getId()
              + " already uses external ID "
              + externalId
              + " but has a different name");
    }
    return existing.getId();
  }

  /*
   * Pushes the client's current details to Fineract. The pending flag is only
   * cleared once Fineract has accepted them, so a failure is retried later.
   */
  private void pushUpdateToFineract(Client client) {
    try {
      fineractClient.updateClient(
          client.getFineractClientId(),
          FineractUpdateClientRequest.builder()
              .firstname(client.getFirstName())
              .lastname(client.getLastName())
              .mobileNo(client.getPhoneNumber())
              .emailAddress(client.getEmail())
              .build());

      client.setFineractSyncPending(false);
      client.setFineractSyncError(null);
      clientRepository.save(client);
      log.info("Client {} updated in Fineract", client.getId());

    } catch (Exception e) {
      log.error("Failed to update client {} in Fineract, will retry later", client.getId(), e);
      recordSyncError(client, e);
    }
  }

  /*
   * Keeps the reason on the client so a stuck sync is visible through the API.
   * If the client was changed while we were talking to Fineract, our copy is
   * out of date and cannot be saved; the next sync run starts from the
   * current version instead.
   */
  private void recordSyncError(Client client, Exception e) {
    client.setFineractSyncError(describe(e));
    try {
      clientRepository.save(client);
    } catch (OptimisticLockingFailureException conflict) {
      log.warn(
          "Client {} changed during Fineract sync, will retry on the next run", client.getId());
    }
  }

  /* Prefers Fineract's own explanation when it sent one. */
  private String describe(Exception e) {
    String message = e.getMessage();
    if (e instanceof FeignException feignException) {
      Matcher fineractMessage = FINERACT_USER_MESSAGE.matcher(feignException.contentUTF8());
      if (fineractMessage.find()) {
        message = fineractMessage.group(1);
      }
    }
    if (message == null) {
      return e.getClass().getSimpleName();
    }
    return message.length() <= MAX_SYNC_ERROR_LENGTH
        ? message
        : message.substring(0, MAX_SYNC_ERROR_LENGTH);
  }

  private RuntimeException duplicateFor(CreateClientRequest request) {
    if (clientRepository.existsByPhoneNumber(request.getPhoneNumber())) {
      return new DuplicatePhoneNumberException(
          "Client with phone number " + request.getPhoneNumber() + " already exists");
    }
    return new DuplicateEmailException(
        "Client with email " + request.getEmail() + " already exists");
  }

  public ClientResponse getClientById(Long id) {
    log.info("Fetching client with ID: {}", id);
    Client client =
        clientRepository
            .findById(id)
            .orElseThrow(() -> new ClientNotFoundException("Client not found with ID: " + id));
    return ClientMapper.toResponse(client);
  }

  /* Oldest first, so a client keeps its place as new ones are added. */
  public PageResponse<ClientResponse> getClients(int page, int size) {
    log.info("Fetching clients, page {} of size {}", page, size);
    Page<Client> clients =
        clientRepository.findAll(Paging.request(page, size, Sort.by(Sort.Direction.ASC, "id")));
    return PageResponse.of(clients, clients.map(ClientMapper::toResponse).getContent());
  }

  /*
   * expectedVersion is the version the caller last read. When given, the
   * update is refused if the client has changed since, so one person's edit
   * cannot silently undo another's.
   */
  public ClientResponse updateClient(Long id, CreateClientRequest request, Long expectedVersion) {
    log.info("Updating client with ID: {}", id);
    Client client =
        clientRepository
            .findById(id)
            .orElseThrow(() -> new ClientNotFoundException("Client not found with ID: " + id));

    if (expectedVersion != null && !expectedVersion.equals(client.getVersion())) {
      throw new ClientChangedException(
          "Client "
              + id
              + " has changed since version "
              + expectedVersion
              + " (now version "
              + client.getVersion()
              + "). Reload it and try again.");
    }

    if (!client.getEmail().equalsIgnoreCase(request.getEmail())
        && clientRepository.existsByEmail(request.getEmail())) {
      throw new DuplicateEmailException(
          "Client with email " + request.getEmail() + " already exists");
    }

    if (!client.getPhoneNumber().equals(request.getPhoneNumber())
        && clientRepository.existsByPhoneNumber(request.getPhoneNumber())) {
      throw new DuplicatePhoneNumberException(
          "Client with phone number " + request.getPhoneNumber() + " already exists");
    }

    client.setFirstName(request.getFirstName());
    client.setLastName(request.getLastName());
    client.setEmail(request.getEmail());
    client.setPhoneNumber(request.getPhoneNumber());
    client.setDateOfBirth(request.getDateOfBirth());

    // A client that is not in Fineract yet is created there with these details
    // by the retry job, so only already-linked clients need an update pushed.
    boolean linkedToFineract = client.getFineractClientId() != null;
    client.setFineractSyncPending(linkedToFineract);

    Client savedClient;
    try {
      savedClient = clientRepository.save(client);
    } catch (DataIntegrityViolationException e) {
      throw duplicateFor(request);
    }

    if (linkedToFineract) {
      pushUpdateToFineract(savedClient);
    }

    return ClientMapper.toResponse(savedClient);
  }
}
