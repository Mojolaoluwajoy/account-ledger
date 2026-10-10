package com.corebanking.savingsservice.repository;

import com.corebanking.savingsservice.entity.SavingsAccount;
import com.corebanking.savingsservice.enums.SavingsAccountStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SavingsAccountRepository extends JpaRepository<SavingsAccount, Long> {

  /*
   * Loads the account with a row lock (SELECT ... FOR UPDATE) so concurrent
   * deposits/withdrawals on the same account run one after another instead of
   * overwriting each other's balance. Must be called inside a transaction.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from SavingsAccount a where a.id = :id")
  Optional<SavingsAccount> findByIdForUpdate(@Param("id") Long id);

  List<SavingsAccount> findByClientId(Long clientId);

  Optional<SavingsAccount> findByAccountNumber(String accountNumber);

  boolean existsByAccountNumber(String accountNumber);

  List<SavingsAccount> findByClientIdAndStatus(Long clientId, SavingsAccountStatus status);
}
