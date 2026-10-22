package com.accountledger.fixeddepositservice.service;

import com.accountledger.fixeddepositservice.dto.CreateFixedDepositRequest;
import com.accountledger.fixeddepositservice.dto.FixedDepositResponse;
import com.accountledger.fixeddepositservice.entity.FixedDeposit;
import com.accountledger.fixeddepositservice.enums.FixedDepositStatus;
import com.accountledger.fixeddepositservice.enums.PayoutStatus;
import com.accountledger.fixeddepositservice.exception.FixedDepositNotActiveException;
import com.accountledger.fixeddepositservice.exception.FixedDepositNotFoundException;
import com.accountledger.fixeddepositservice.exception.PrematureWithdrawalException;
import com.accountledger.fixeddepositservice.repository.FixedDepositRepository;
import com.accountledger.fixeddepositservice.util.ClientServiceClient;
import com.accountledger.fixeddepositservice.util.FixedDepositMapper;
import com.accountledger.fixeddepositservice.util.SavingsServiceClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixedDepositService {

  private final FixedDepositRepository fixedDepositRepository;
  private final LedgerPostingService ledgerPostingService;
  private final ClientServiceClient clientServiceClient;
  private final SavingsServiceClient savingsServiceClient;

  /* Percentage points taken off the agreed rate when a deposit is withdrawn early. */
  @Value("${fixed-deposit.early-withdrawal.penalty-rate:2.00}")
  private BigDecimal earlyWithdrawalPenaltyRate = new BigDecimal("2.00");

  @Transactional
  public FixedDepositResponse createFixedDeposit(CreateFixedDepositRequest request) {
    log.info("Creating fixed deposit for client ID: {}", request.getClientId());

    clientServiceClient.requireActiveClient(request.getClientId());
    savingsServiceClient.requirePayoutAccount(request.getPayoutAccountId(), request.getClientId());

    LocalDate depositDate = LocalDate.now();
    LocalDate maturityDate = depositDate.plusDays(request.getTenureInDays());
    BigDecimal maturityAmount =
        calculateMaturityAmount(
            request.getPrincipalAmount(), request.getInterestRate(), request.getTenureInDays());

    FixedDeposit deposit = new FixedDeposit();
    deposit.setClientId(request.getClientId());
    deposit.setPrincipalAmount(request.getPrincipalAmount());
    deposit.setInterestRate(request.getInterestRate());
    deposit.setTenureInDays(request.getTenureInDays());
    deposit.setDepositDate(depositDate);
    deposit.setMaturityDate(maturityDate);
    deposit.setMaturityAmount(maturityAmount);
    deposit.setStatus(FixedDepositStatus.ACTIVE);
    deposit.setPayoutAccountId(request.getPayoutAccountId());

    FixedDeposit savedDeposit = fixedDepositRepository.save(deposit);
    log.info(
        "Fixed deposit created with ID: {} maturing on: {}", savedDeposit.getId(), maturityDate);

    String reference = "FD-" + savedDeposit.getId();
    ledgerPostingService.record(
        "FIXED_DEPOSIT_CREATION",
        request.getPrincipalAmount(),
        reference,
        "Fixed deposit creation - " + reference);

    return FixedDepositMapper.toResponse(savedDeposit);
  }

  @Transactional
  public FixedDepositResponse matureDeposit(Long depositId) {
    log.info("Processing maturity for deposit ID: {}", depositId);

    FixedDeposit deposit =
        fixedDepositRepository
            .findByIdForUpdate(depositId)
            .orElseThrow(
                () ->
                    new FixedDepositNotFoundException(
                        "Fixed deposit not found with ID: " + depositId));

    if (deposit.getStatus() != FixedDepositStatus.ACTIVE) {
      throw new FixedDepositNotActiveException(
          "Fixed deposit "
              + depositId
              + " cannot be matured. Current status: "
              + deposit.getStatus());
    }

    if (LocalDate.now().isBefore(deposit.getMaturityDate())) {
      throw new PrematureWithdrawalException(
          "Fixed deposit "
              + depositId
              + " cannot be matured before "
              + deposit.getMaturityDate()
              + ". Today is "
              + LocalDate.now()
              + ". Use withdraw-early to end it now with reduced interest.");
    }

    deposit.setStatus(FixedDepositStatus.MATURED);
    BigDecimal interest = deposit.getMaturityAmount().subtract(deposit.getPrincipalAmount());
    log.info("Fixed deposit {} matured", depositId);
    return FixedDepositMapper.toResponse(payOut(deposit, interest));
  }

  /*
   * Withdraws a deposit before its maturity date. Interest is paid for the
   * days actually held, at the agreed rate minus the early withdrawal penalty
   * (never below zero), and the deposit is paid out like a matured one.
   */
  @Transactional
  public FixedDepositResponse withdrawEarly(Long depositId) {
    log.info("Processing early withdrawal for deposit ID: {}", depositId);

    FixedDeposit deposit =
        fixedDepositRepository
            .findByIdForUpdate(depositId)
            .orElseThrow(
                () ->
                    new FixedDepositNotFoundException(
                        "Fixed deposit not found with ID: " + depositId));

    if (deposit.getStatus() != FixedDepositStatus.ACTIVE) {
      throw new FixedDepositNotActiveException(
          "Fixed deposit "
              + depositId
              + " cannot be withdrawn. Current status: "
              + deposit.getStatus());
    }

    LocalDate today = LocalDate.now();
    if (!today.isBefore(deposit.getMaturityDate())) {
      throw new FixedDepositNotActiveException(
          "Fixed deposit "
              + depositId
              + " has reached its maturity date ("
              + deposit.getMaturityDate()
              + "). Mature it instead to receive the full interest.");
    }

    BigDecimal reducedRate =
        deposit.getInterestRate().subtract(earlyWithdrawalPenaltyRate).max(BigDecimal.ZERO);
    int daysHeld = (int) ChronoUnit.DAYS.between(deposit.getDepositDate(), today);
    BigDecimal interest =
        calculateMaturityAmount(deposit.getPrincipalAmount(), reducedRate, daysHeld)
            .subtract(deposit.getPrincipalAmount());

    deposit.setStatus(FixedDepositStatus.CLOSED_EARLY);
    deposit.setClosedOn(today);
    log.info(
        "Fixed deposit {} withdrawn early after {} days: interest {} at {}% instead of {}%",
        depositId, daysHeld, interest, reducedRate, deposit.getInterestRate());
    return FixedDepositMapper.toResponse(payOut(deposit, interest));
  }

  /*
   * Pays principal and interest out of a deposit that has ended. With a
   * payout account the money goes there: FixedDepositPayoutService sends it
   * shortly after this commits, and the savings service posts the ledger
   * entries when it credits the account.
   */
  private FixedDeposit payOut(FixedDeposit deposit, BigDecimal interest) {
    deposit.setPayoutInterest(interest);

    if (deposit.getPayoutAccountId() != null) {
      deposit.setPayoutStatus(PayoutStatus.PENDING);
      log.info(
          "Paying fixed deposit {} into savings account {}",
          deposit.getId(),
          deposit.getPayoutAccountId());
      return fixedDepositRepository.save(deposit);
    }

    // Deposits created before payouts existed have no payout account and are
    // paid out in cash, as they always were.
    FixedDeposit saved = fixedDepositRepository.save(deposit);
    log.info("Fixed deposit {} paid out in cash", deposit.getId());

    // Principal and interest are posted separately: repaying principal settles
    // what we owed the customer, while the interest is an expense.
    String principalReference = "FD-MAT-" + deposit.getId();
    ledgerPostingService.record(
        "FIXED_DEPOSIT_MATURITY",
        saved.getPrincipalAmount(),
        principalReference,
        "Fixed deposit payout (principal) - " + principalReference);

    if (interest.compareTo(BigDecimal.ZERO) > 0) {
      String interestReference = "FD-INT-" + deposit.getId();
      ledgerPostingService.record(
          "FIXED_DEPOSIT_INTEREST",
          interest,
          interestReference,
          "Fixed deposit payout (interest) - " + interestReference);
    }
    return saved;
  }

  public FixedDepositResponse getDepositById(Long depositId) {
    log.info("Fetching fixed deposit with ID: {}", depositId);

    FixedDeposit deposit =
        fixedDepositRepository
            .findById(depositId)
            .orElseThrow(
                () ->
                    new FixedDepositNotFoundException(
                        "Fixed deposit not found with ID: " + depositId));

    return FixedDepositMapper.toResponse(deposit);
  }

  public List<FixedDepositResponse> getDepositsByClientId(Long clientId) {
    log.info("Fetching fixed deposits for client ID: {}", clientId);

    return fixedDepositRepository.findByClientId(clientId).stream()
        .map(FixedDepositMapper::toResponse)
        .collect(Collectors.toList());
  }

  private BigDecimal calculateMaturityAmount(
      BigDecimal principal, BigDecimal annualRate, int tenureInDays) {

    BigDecimal rate = annualRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
    BigDecimal tenure =
        BigDecimal.valueOf(tenureInDays).divide(BigDecimal.valueOf(365), 10, RoundingMode.HALF_UP);
    BigDecimal interest = principal.multiply(rate).multiply(tenure);

    return principal.add(interest).setScale(2, RoundingMode.HALF_UP);
  }
}
