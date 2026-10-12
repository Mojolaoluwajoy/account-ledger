package com.corebanking.fixeddepositservice.service;

import com.corebanking.fixeddepositservice.dto.CreateFixedDepositRequest;
import com.corebanking.fixeddepositservice.dto.FixedDepositResponse;
import com.corebanking.fixeddepositservice.entity.FixedDeposit;
import com.corebanking.fixeddepositservice.enums.FixedDepositStatus;
import com.corebanking.fixeddepositservice.exception.FixedDepositNotActiveException;
import com.corebanking.fixeddepositservice.exception.FixedDepositNotFoundException;
import com.corebanking.fixeddepositservice.exception.PrematureWithdrawalException;
import com.corebanking.fixeddepositservice.repository.FixedDepositRepository;
import com.corebanking.fixeddepositservice.util.ClientServiceClient;
import com.corebanking.fixeddepositservice.util.FixedDepositMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixedDepositService {

  private final FixedDepositRepository fixedDepositRepository;
  private final LedgerPostingService ledgerPostingService;
  private final ClientServiceClient clientServiceClient;

  @Transactional
  public FixedDepositResponse createFixedDeposit(CreateFixedDepositRequest request) {
    log.info("Creating fixed deposit for client ID: {}", request.getClientId());

    clientServiceClient.requireActiveClient(request.getClientId());

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
              + LocalDate.now());
    }

    deposit.setStatus(FixedDepositStatus.MATURED);
    FixedDeposit maturedDeposit = fixedDepositRepository.save(deposit);
    log.info("Fixed deposit {} matured successfully", depositId);

    // Principal and interest are posted separately: repaying principal settles
    // what we owed the customer, while the interest is an expense.
    String principalReference = "FD-MAT-" + depositId;
    ledgerPostingService.record(
        "FIXED_DEPOSIT_MATURITY",
        maturedDeposit.getPrincipalAmount(),
        principalReference,
        "Fixed deposit maturity (principal) - " + principalReference);

    BigDecimal interest =
        maturedDeposit.getMaturityAmount().subtract(maturedDeposit.getPrincipalAmount());
    if (interest.compareTo(BigDecimal.ZERO) > 0) {
      String interestReference = "FD-INT-" + depositId;
      ledgerPostingService.record(
          "FIXED_DEPOSIT_INTEREST",
          interest,
          interestReference,
          "Fixed deposit maturity (interest) - " + interestReference);
    }

    return FixedDepositMapper.toResponse(maturedDeposit);
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
