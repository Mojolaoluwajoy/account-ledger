package com.accountledger.savingsservice.repository;

import com.accountledger.savingsservice.entity.SavingsTransaction;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SavingsTransactionRepository extends JpaRepository<SavingsTransaction, Long> {

  Page<SavingsTransaction> findByAccountId(Long accountId, Pageable pageable);

  Optional<SavingsTransaction> findByReference(String reference);

  boolean existsByReference(String reference);

  /* The latest transaction before a moment: its balanceAfter is the balance at that moment. */
  Optional<SavingsTransaction>
      findFirstByAccountIdAndTransactionDateBeforeOrderByTransactionDateDescIdDesc(
          Long accountId, LocalDateTime before);
}
