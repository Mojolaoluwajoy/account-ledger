package com.accountledger.savingsservice.repository;

import com.accountledger.savingsservice.entity.LedgerPosting;
import com.accountledger.savingsservice.enums.LedgerPostingStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerPostingRepository extends JpaRepository<LedgerPosting, Long> {

  List<LedgerPosting> findTop50ByStatusOrderByIdAsc(LedgerPostingStatus status);

  List<LedgerPosting> findByStatusOrderByIdAsc(LedgerPostingStatus status);
}
