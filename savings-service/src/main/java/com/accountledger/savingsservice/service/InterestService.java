package com.accountledger.savingsservice.service;

import com.accountledger.savingsservice.entity.InterestAccrual;
import com.accountledger.savingsservice.entity.SavingsAccount;
import com.accountledger.savingsservice.entity.SavingsTransaction;
import com.accountledger.savingsservice.enums.SavingsAccountStatus;
import com.accountledger.savingsservice.enums.TransactionType;
import com.accountledger.savingsservice.repository.InterestAccrualRepository;
import com.accountledger.savingsservice.repository.SavingsAccountRepository;
import com.accountledger.savingsservice.repository.SavingsTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * Savings interest, worked out daily and paid monthly.
 *
 * Each day an account earns balance x annual rate / 365 on its end-of-day
 * balance, kept to 6 decimal places. Nothing is earned on a day the balance
 * ends below the product's minimum balance. On the first of the month the
 * unpaid days are added up, rounded half-up to 2 places and paid into the
 * account. A total that rounds to zero is carried into the next month.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterestService {

  private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365);
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final int ACCRUAL_SCALE = 6;
  private static final String LEDGER_INTEREST = "SAVINGS_INTEREST";

  private final SavingsAccountRepository savingsAccountRepository;
  private final SavingsTransactionRepository savingsTransactionRepository;
  private final InterestAccrualRepository interestAccrualRepository;
  private final LedgerPostingService ledgerPostingService;

  /*
   * Records the interest earned on every day from the last recorded day (or
   * the day the account was opened) up to and including lastDay. Days already
   * recorded are skipped, so missed runs are caught up and repeated runs
   * change nothing. Returns how many days were recorded.
   */
  @Transactional
  public int accrue(Long accountId, LocalDate lastDay) {
    SavingsAccount account = savingsAccountRepository.findById(accountId).orElseThrow();
    if (account.getStatus() != SavingsAccountStatus.ACTIVE) {
      return 0;
    }

    LocalDate day =
        interestAccrualRepository
            .findTopByAccountIdOrderByAccrualDateDesc(accountId)
            .map(last -> last.getAccrualDate().plusDays(1))
            .orElse(account.getCreatedAt().toLocalDate());

    BigDecimal rate = account.getProduct().getNominalAnnualInterestRate();
    BigDecimal minimumBalance = account.getProduct().getMinimumBalance();
    int recorded = 0;

    for (; !day.isAfter(lastDay); day = day.plusDays(1)) {
      Optional<BigDecimal> endOfDayBalance = balanceAtEndOf(accountId, day);
      if (endOfDayBalance.isEmpty()) {
        continue; // no money in the account yet that day
      }
      BigDecimal balance = endOfDayBalance.get();

      BigDecimal amount = BigDecimal.ZERO.setScale(ACCRUAL_SCALE);
      if (balance.compareTo(minimumBalance) >= 0) {
        amount =
            balance
                .multiply(rate)
                .divide(HUNDRED.multiply(DAYS_IN_YEAR), ACCRUAL_SCALE, RoundingMode.HALF_UP);
      }

      InterestAccrual accrual = new InterestAccrual();
      accrual.setAccountId(accountId);
      accrual.setAccrualDate(day);
      accrual.setBalance(balance);
      accrual.setAnnualRate(rate);
      accrual.setAmount(amount);
      interestAccrualRepository.save(accrual);
      recorded++;
    }
    return recorded;
  }

  /*
   * Pays the interest recorded for days before monthStart into the account,
   * as one INTEREST transaction for the month before monthStart. Returns the
   * amount paid, or zero if nothing was paid.
   */
  @Transactional
  public BigDecimal post(Long accountId, LocalDate monthStart) {
    SavingsAccount account = savingsAccountRepository.findByIdForUpdate(accountId).orElseThrow();
    if (account.getStatus() != SavingsAccountStatus.ACTIVE) {
      return BigDecimal.ZERO;
    }

    YearMonth month = YearMonth.from(monthStart).minusMonths(1);
    String reference = "INT-" + account.getAccountNumber() + "-" + month;
    if (savingsTransactionRepository.existsByReference(reference)) {
      // Already paid for this month. Any days recorded late are paid with
      // next month's interest.
      return BigDecimal.ZERO;
    }

    List<InterestAccrual> unpaid =
        interestAccrualRepository.findByAccountIdAndPostingReferenceIsNullAndAccrualDateBefore(
            accountId, monthStart);
    // A month that rounds to zero is left unpaid and carried into the next.
    return pay(account, unpaid, reference, "Interest for " + month, false);
  }

  /*
   * Pays every day of interest recorded so far into an account that is being
   * closed, whatever month it belongs to. The days are marked paid even when
   * they round to zero, so nothing is left behind on the closed account.
   */
  @Transactional
  public BigDecimal payOnClosure(Long accountId) {
    SavingsAccount account = savingsAccountRepository.findByIdForUpdate(accountId).orElseThrow();
    List<InterestAccrual> unpaid =
        interestAccrualRepository.findByAccountIdAndPostingReferenceIsNull(accountId);
    String reference = "INT-" + account.getAccountNumber() + "-CLOSE";
    return pay(account, unpaid, reference, "Interest up to closure", true);
  }

  private BigDecimal pay(
      SavingsAccount account,
      List<InterestAccrual> unpaid,
      String reference,
      String description,
      boolean settleEvenIfZero) {
    BigDecimal amount =
        unpaid.stream()
            .map(InterestAccrual::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    if (amount.signum() == 0) {
      if (settleEvenIfZero) {
        unpaid.forEach(accrual -> accrual.setPostingReference(reference));
        interestAccrualRepository.saveAll(unpaid);
      }
      return BigDecimal.ZERO;
    }

    BigDecimal balanceBefore = account.getBalance();
    BigDecimal newBalance = balanceBefore.add(amount);
    account.setBalance(newBalance);
    account.setAvailableBalance(newBalance);
    savingsAccountRepository.save(account);

    SavingsTransaction transaction = new SavingsTransaction();
    transaction.setAccount(account);
    transaction.setTransactionType(TransactionType.INTEREST);
    transaction.setAmount(amount);
    transaction.setBalanceBefore(balanceBefore);
    transaction.setBalanceAfter(newBalance);
    transaction.setDescription(description);
    transaction.setReference(reference);
    savingsTransactionRepository.save(transaction);

    unpaid.forEach(accrual -> accrual.setPostingReference(reference));
    interestAccrualRepository.saveAll(unpaid);

    ledgerPostingService.record(
        LEDGER_INTEREST, amount, reference, description + " - " + account.getAccountNumber());
    log.info("Paid interest of {} into account {}: {}", amount, account.getId(), description);
    return amount;
  }

  /* The account's balance at the end of the day, or empty if it had no money in it yet. */
  private Optional<BigDecimal> balanceAtEndOf(Long accountId, LocalDate day) {
    return savingsTransactionRepository
        .findFirstByAccountIdAndTransactionDateBeforeOrderByTransactionDateDescIdDesc(
            accountId, day.plusDays(1).atStartOfDay())
        .map(SavingsTransaction::getBalanceAfter);
  }
}
