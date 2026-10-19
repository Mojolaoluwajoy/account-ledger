package com.corebanking.savingsservice.repository;

import com.corebanking.savingsservice.entity.SavingsTransaction;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SavingsTransactionRepository extends JpaRepository<SavingsTransaction, Long> {

  Page<SavingsTransaction> findByAccountId(Long accountId, Pageable pageable);

  Optional<SavingsTransaction> findByReference(String reference);
}
