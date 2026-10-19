package com.accountledger.ledgerservice.config;

import com.accountledger.ledgerservice.entity.GlAccount;
import com.accountledger.ledgerservice.enums.GlAccountType;
import com.accountledger.ledgerservice.repository.GlAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GlAccountInitializer implements CommandLineRunner {

  private final GlAccountRepository glAccountRepository;

  @Override
  public void run(String... args) {
    if (glAccountRepository.count() == 0) {
      log.info("Seeding Chart of Accounts...");

      createAccount("1001", "Cash", GlAccountType.ASSET, "Cash and cash equivalents");
      createAccount("1002", "Fixed Deposit Asset", GlAccountType.ASSET, "Fixed deposit placements");
      createAccount(
          "2001", "Savings Control", GlAccountType.LIABILITY, "Customer savings balances");
      createAccount(
          "2002", "Fixed Deposit Liability", GlAccountType.LIABILITY, "Fixed deposit obligations");
      createAccount("4001", "Interest Income", GlAccountType.INCOME, "Interest earned on deposits");
      createAccount(
          "5001", "Interest Expense", GlAccountType.EXPENSE, "Interest paid to customers");

      log.info("Chart of Accounts seeded successfully");
    }
  }

  private void createAccount(String code, String name, GlAccountType type, String description) {
    GlAccount account = new GlAccount();
    account.setAccountCode(code);
    account.setAccountName(name);
    account.setAccountType(type);
    account.setDescription(description);
    glAccountRepository.save(account);
    log.info("Created GL Account: {} - {}", code, name);
  }
}
