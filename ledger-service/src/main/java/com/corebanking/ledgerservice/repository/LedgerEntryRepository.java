package com.corebanking.ledgerservice.repository;

import com.corebanking.ledgerservice.entity.LedgerEntry;
import com.corebanking.ledgerservice.enums.TransactionEntryType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
  List<LedgerEntry> findByTransactionReference(String transactionReference);

  Optional<LedgerEntry> findFirstByTransactionReference(String transactionReference);

  List<LedgerEntry> findByTransactionTypeOrderByEntryDateDesc(TransactionEntryType transactionType);
}
