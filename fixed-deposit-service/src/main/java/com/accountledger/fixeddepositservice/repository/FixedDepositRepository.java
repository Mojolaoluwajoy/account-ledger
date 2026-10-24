package com.accountledger.fixeddepositservice.repository;

import com.accountledger.fixeddepositservice.entity.FixedDeposit;
import com.accountledger.fixeddepositservice.enums.FixedDepositStatus;
import com.accountledger.fixeddepositservice.enums.PayoutStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FixedDepositRepository extends JpaRepository<FixedDeposit, Long> {

  /*
   * Loads the deposit with a row lock (SELECT ... FOR UPDATE) so two maturity
   * requests for the same deposit cannot both pay it out. Must be called
   * inside a transaction.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from FixedDeposit d where d.id = :id")
  Optional<FixedDeposit> findByIdForUpdate(@Param("id") Long id);

  List<FixedDeposit> findByClientId(Long clientId);

  List<FixedDeposit> findTop50ByPayoutStatusOrderByIdAsc(PayoutStatus payoutStatus);

  List<FixedDeposit> findByPayoutStatusOrderByIdAsc(PayoutStatus payoutStatus);

  List<FixedDeposit> findByStatus(FixedDepositStatus status);

  List<FixedDeposit> findTop50ByStatusAndCreatedAtBeforeOrderByIdAsc(
      FixedDepositStatus status, LocalDateTime createdBefore);

  List<FixedDeposit> findByStatusAndMaturityDateLessThanEqual(
      FixedDepositStatus status, LocalDate date);
}
