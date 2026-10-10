package com.corebanking.fixeddepositservice.repository;

import com.corebanking.fixeddepositservice.entity.LedgerPosting;
import com.corebanking.fixeddepositservice.enums.LedgerPostingStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerPostingRepository extends JpaRepository<LedgerPosting, Long> {

  List<LedgerPosting> findTop50ByStatusOrderByIdAsc(LedgerPostingStatus status);
}
