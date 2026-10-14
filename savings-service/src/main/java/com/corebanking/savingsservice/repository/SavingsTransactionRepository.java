package com.corebanking.savingsservice.repository;

import com.corebanking.savingsservice.entity.SavingsTransaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SavingsTransactionRepository extends JpaRepository<SavingsTransaction, Long> {

  List<SavingsTransaction> findByAccountIdOrderByTransactionDateDesc(Long accountId);

  Optional<SavingsTransaction> findByReference(String reference);
}
