package com.accountledger.savingsservice.repository;

import com.accountledger.savingsservice.entity.InterestAccrual;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InterestAccrualRepository extends JpaRepository<InterestAccrual, Long> {

  Optional<InterestAccrual> findTopByAccountIdOrderByAccrualDateDesc(Long accountId);

  List<InterestAccrual> findByAccountIdAndPostingReferenceIsNullAndAccrualDateBefore(
      Long accountId, LocalDate before);

  @Query(
      "select distinct a.accountId from InterestAccrual a"
          + " where a.postingReference is null and a.accrualDate < :before")
  List<Long> findAccountIdsWithUnpostedAccrualsBefore(@Param("before") LocalDate before);
}
