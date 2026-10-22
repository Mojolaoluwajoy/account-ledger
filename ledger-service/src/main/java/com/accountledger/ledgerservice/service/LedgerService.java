package com.accountledger.ledgerservice.service;

import com.accountledger.ledgerservice.dto.LedgerEntryResponse;
import com.accountledger.ledgerservice.dto.PageResponse;
import com.accountledger.ledgerservice.dto.PostLedgerEntryRequest;
import com.accountledger.ledgerservice.entity.*;
import com.accountledger.ledgerservice.exception.GlAccountNotFoundException;
import com.accountledger.ledgerservice.exception.LedgerEntryNotFoundException;
import com.accountledger.ledgerservice.exception.ReferenceConflictException;
import com.accountledger.ledgerservice.repository.GlAccountRepository;
import com.accountledger.ledgerservice.repository.LedgerEntryRepository;
import com.accountledger.ledgerservice.util.LedgerEntryMapper;
import com.accountledger.ledgerservice.util.Paging;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

  private final LedgerEntryRepository ledgerEntryRepository;
  private final GlAccountRepository glAccountRepository;

  @Transactional
  public LedgerEntryResponse postEntry(PostLedgerEntryRequest request) {
    log.info("Posting ledger entry for transaction type: {}", request.getTransactionType());

    /*
     * Callers retry when they are unsure a posting went through, so the same
     * reference must never create a second entry. A retry carries the same
     * type and amount; a reference reused for a different posting is refused
     * rather than answered as if it had been posted.
     */
    if (request.getTransactionReference() != null) {
      Optional<LedgerEntry> existing =
          ledgerEntryRepository.findFirstByTransactionReference(request.getTransactionReference());
      if (existing.isPresent()) {
        LedgerEntry posted = existing.get();
        if (posted.getTransactionType() != request.getTransactionType()
            || posted.getAmount().compareTo(request.getAmount()) != 0) {
          throw new ReferenceConflictException(
              "Reference "
                  + request.getTransactionReference()
                  + " was already posted as "
                  + posted.getTransactionType()
                  + " of "
                  + posted.getAmount());
        }
        log.info(
            "Ledger entry already posted for reference: {}", request.getTransactionReference());
        return LedgerEntryMapper.toResponse(existing.get());
      }
    }

    String debitAccountCode;
    String creditAccountCode;

    switch (request.getTransactionType()) {
      case SAVINGS_DEPOSIT -> {
        debitAccountCode = "1001";
        creditAccountCode = "2001";
      }
      case SAVINGS_WITHDRAWAL -> {
        debitAccountCode = "2001";
        creditAccountCode = "1001";
      }
      // Customer places a fixed deposit: cash comes in, we owe it back.
      case FIXED_DEPOSIT_CREATION -> {
        debitAccountCode = "1001";
        creditAccountCode = "2002";
      }
      // Principal repaid at maturity: the obligation is settled in cash.
      case FIXED_DEPOSIT_MATURITY -> {
        debitAccountCode = "2002";
        creditAccountCode = "1001";
      }
      // Interest paid at maturity: an expense, paid in cash.
      case FIXED_DEPOSIT_INTEREST -> {
        debitAccountCode = "5001";
        creditAccountCode = "1001";
      }
      // Principal paid into the customer's savings account at maturity: the
      // fixed deposit obligation becomes a savings obligation.
      case FIXED_DEPOSIT_PAYOUT -> {
        debitAccountCode = "2002";
        creditAccountCode = "2001";
      }
      // Interest paid into the customer's savings account at maturity.
      case FIXED_DEPOSIT_INTEREST_PAYOUT -> {
        debitAccountCode = "5001";
        creditAccountCode = "2001";
      }
      // Monthly interest paid into a savings account: an expense, owed to
      // the customer as savings.
      case SAVINGS_INTEREST -> {
        debitAccountCode = "5001";
        creditAccountCode = "2001";
      }
      default ->
          throw new IllegalArgumentException(
              "Unknown transaction type: " + request.getTransactionType());
    }

    GlAccount debitAccount =
        glAccountRepository
            .findByAccountCode(debitAccountCode)
            .orElseThrow(
                () ->
                    new GlAccountNotFoundException(
                        "GL Account not found with code: " + debitAccountCode));

    GlAccount creditAccount =
        glAccountRepository
            .findByAccountCode(creditAccountCode)
            .orElseThrow(
                () ->
                    new GlAccountNotFoundException(
                        "GL Account not found with code: " + creditAccountCode));

    LedgerEntry entry = new LedgerEntry();
    entry.setTransactionReference(request.getTransactionReference());
    entry.setTransactionType(request.getTransactionType());
    entry.setDebitAccount(debitAccount);
    entry.setCreditAccount(creditAccount);
    entry.setAmount(request.getAmount());
    entry.setDescription(request.getDescription());

    LedgerEntry savedEntry = ledgerEntryRepository.save(entry);
    log.info("Ledger entry posted successfully with ID: {}", savedEntry.getId());

    return LedgerEntryMapper.toResponse(savedEntry);
  }

  public List<LedgerEntryResponse> getEntriesByReference(String reference) {
    return ledgerEntryRepository.findByTransactionReference(reference).stream()
        .map(LedgerEntryMapper::toResponse)
        .collect(Collectors.toList());
  }

  /* Newest first. */
  public PageResponse<LedgerEntryResponse> getEntries(int page, int size) {
    Page<LedgerEntry> entries =
        ledgerEntryRepository.findAll(
            Paging.request(page, size, Sort.by(Sort.Direction.DESC, "entryDate", "id")));
    return PageResponse.of(entries, entries.map(LedgerEntryMapper::toResponse).getContent());
  }

  public LedgerEntryResponse getEntryById(Long id) {
    LedgerEntry entry =
        ledgerEntryRepository
            .findById(id)
            .orElseThrow(
                () -> new LedgerEntryNotFoundException("Ledger entry not found with ID: " + id));
    return LedgerEntryMapper.toResponse(entry);
  }
}
