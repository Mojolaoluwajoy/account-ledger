package com.accountledger.ledgerservice.repository;

import com.accountledger.ledgerservice.entity.GlAccount;
import com.accountledger.ledgerservice.enums.GlAccountType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GlAccountRepository extends JpaRepository<GlAccount, Long> {
  Optional<GlAccount> findByAccountCode(String accountCode);

  List<GlAccount> findByAccountType(GlAccountType accountType);
}
