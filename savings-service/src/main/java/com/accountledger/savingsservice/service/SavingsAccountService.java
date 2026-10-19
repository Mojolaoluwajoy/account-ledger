package com.accountledger.savingsservice.service;

import com.accountledger.savingsservice.dto.FixedDepositPayoutRequest;
import com.accountledger.savingsservice.dto.OpenSavingsAccountRequest;
import com.accountledger.savingsservice.dto.PageResponse;
import com.accountledger.savingsservice.dto.SavingsAccountResponse;
import com.accountledger.savingsservice.dto.SavingsTransactionResponse;
import com.accountledger.savingsservice.dto.TransactionRequest;
import com.accountledger.savingsservice.dto.TransactionResponse;
import com.accountledger.savingsservice.dto.TransferRequest;
import com.accountledger.savingsservice.dto.TransferResponse;
import com.accountledger.savingsservice.entity.SavingsAccount;
import com.accountledger.savingsservice.entity.SavingsProduct;
import com.accountledger.savingsservice.entity.SavingsTransaction;
import com.accountledger.savingsservice.enums.SavingsAccountStatus;
import com.accountledger.savingsservice.enums.TransactionType;
import com.accountledger.savingsservice.exception.AccountNotActiveException;
import com.accountledger.savingsservice.exception.AccountNotFoundException;
import com.accountledger.savingsservice.exception.InsufficientBalanceException;
import com.accountledger.savingsservice.exception.InvalidTransferException;
import com.accountledger.savingsservice.exception.PayoutReferenceConflictException;
import com.accountledger.savingsservice.exception.ProductNotFoundException;
import com.accountledger.savingsservice.repository.SavingsAccountRepository;
import com.accountledger.savingsservice.repository.SavingsProductRepository;
import com.accountledger.savingsservice.repository.SavingsTransactionRepository;
import com.accountledger.savingsservice.util.ClientServiceClient;
import com.accountledger.savingsservice.util.Paging;
import com.accountledger.savingsservice.util.SavingsAccountMapper;
import com.accountledger.savingsservice.util.SavingsTransactionMapper;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavingsAccountService {

  private static final String LEDGER_DEPOSIT = "SAVINGS_DEPOSIT";
  private static final String LEDGER_WITHDRAWAL = "SAVINGS_WITHDRAWAL";
  private static final String LEDGER_FD_PAYOUT = "FIXED_DEPOSIT_PAYOUT";
  private static final String LEDGER_FD_INTEREST_PAYOUT = "FIXED_DEPOSIT_INTEREST_PAYOUT";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final SavingsAccountRepository savingsAccountRepository;
  private final SavingsProductRepository savingsProductRepository;
  private final SavingsTransactionRepository savingsTransactionRepository;
  private final LedgerPostingService ledgerPostingService;
  private final ClientServiceClient clientServiceClient;

  @Transactional
  public SavingsAccountResponse openAccount(OpenSavingsAccountRequest request) {
    log.info("Opening savings account for client ID: {}", request.getClientId());

    clientServiceClient.requireActiveClient(request.getClientId());

    SavingsProduct product =
        savingsProductRepository
            .findById(request.getProductId())
            .orElseThrow(
                () ->
                    new ProductNotFoundException(
                        "Savings product not found with ID: " + request.getProductId()));

    if (request.getInitialDeposit().compareTo(product.getMinimumOpeningBalance()) < 0) {
      throw new InsufficientBalanceException(
          "Initial deposit of "
              + request.getInitialDeposit()
              + " is below the minimum opening balance of "
              + product.getMinimumOpeningBalance());
    }

    SavingsAccount account = new SavingsAccount();
    account.setClientId(request.getClientId());
    account.setProduct(product);
    account.setAccountNumber(generateAccountNumber());
    account.setBalance(request.getInitialDeposit());
    account.setAvailableBalance(request.getInitialDeposit());
    account.setStatus(SavingsAccountStatus.ACTIVE);

    SavingsAccount savedAccount = savingsAccountRepository.save(account);
    log.info("Savings account opened successfully: {}", savedAccount.getAccountNumber());

    recordTransaction(
        savedAccount,
        TransactionType.DEPOSIT,
        LEDGER_DEPOSIT,
        request.getInitialDeposit(),
        BigDecimal.ZERO,
        request.getInitialDeposit(),
        "Opening deposit");

    return SavingsAccountMapper.toResponse(savedAccount);
  }

  @Transactional
  public TransactionResponse deposit(Long accountId, TransactionRequest request) {
    log.info("Processing deposit of {} to account ID: {}", request.getAmount(), accountId);

    SavingsAccount account =
        savingsAccountRepository
            .findByIdForUpdate(accountId)
            .orElseThrow(
                () ->
                    new AccountNotFoundException(
                        "Savings account not found with ID: " + accountId));

    validateAccountIsActive(account);

    BigDecimal balanceBefore = account.getBalance();
    BigDecimal newBalance = balanceBefore.add(request.getAmount());

    account.setBalance(newBalance);
    account.setAvailableBalance(newBalance);
    SavingsAccount savedAccount = savingsAccountRepository.save(account);

    recordTransaction(
        savedAccount,
        TransactionType.DEPOSIT,
        LEDGER_DEPOSIT,
        request.getAmount(),
        balanceBefore,
        newBalance,
        "Savings deposit");
    log.info("Deposit successful. New balance: {}", newBalance);

    return TransactionResponse.builder()
        .accountId(accountId)
        .transactionType("DEPOSIT")
        .amount(request.getAmount())
        .balanceBefore(balanceBefore)
        .newBalance(newBalance)
        .transactionDate(LocalDateTime.now())
        .build();
  }

  @Transactional
  public TransactionResponse withdraw(Long accountId, TransactionRequest request) {
    log.info("Processing withdrawal of {} from account ID: {}", request.getAmount(), accountId);

    SavingsAccount account =
        savingsAccountRepository
            .findByIdForUpdate(accountId)
            .orElseThrow(
                () ->
                    new AccountNotFoundException(
                        "Savings account not found with ID: " + accountId));

    validateAccountIsActive(account);

    BigDecimal balanceBefore = account.getBalance();
    BigDecimal balanceAfterWithdrawal =
        balanceAfterTakingOut(account, request.getAmount(), "Withdrawal");

    account.setBalance(balanceAfterWithdrawal);
    account.setAvailableBalance(balanceAfterWithdrawal);
    savingsAccountRepository.save(account);

    recordTransaction(
        account,
        TransactionType.WITHDRAWAL,
        LEDGER_WITHDRAWAL,
        request.getAmount(),
        balanceBefore,
        balanceAfterWithdrawal,
        "Savings withdrawal");
    log.info("Withdrawal successful. New balance: {}", balanceAfterWithdrawal);

    return TransactionResponse.builder()
        .accountId(accountId)
        .transactionType("WITHDRAWAL")
        .amount(request.getAmount())
        .balanceBefore(balanceBefore)
        .newBalance(balanceAfterWithdrawal)
        .transactionDate(LocalDateTime.now())
        .build();
  }

  /*
   * Moves money between two savings accounts. Both balances and both
   * transaction rows are written in one database transaction, so a transfer
   * either happens completely or not at all.
   *
   * Nothing is posted to the ledger: both accounts sit under the same savings
   * control account, so the bank's books do not change.
   */
  @Transactional
  public TransferResponse transfer(Long sourceAccountId, TransferRequest request) {
    Long destinationAccountId = request.getDestinationAccountId();
    log.info(
        "Processing transfer of {} from account ID: {} to account ID: {}",
        request.getAmount(),
        sourceAccountId,
        destinationAccountId);

    if (sourceAccountId.equals(destinationAccountId)) {
      throw new InvalidTransferException("Cannot transfer to the same account");
    }

    // Always lock the lower ID first. Two transfers going in opposite
    // directions between the same accounts would otherwise each hold one row
    // and wait forever for the other.
    SavingsAccount source;
    SavingsAccount destination;
    if (sourceAccountId < destinationAccountId) {
      source = lockAccount(sourceAccountId);
      destination = lockAccount(destinationAccountId);
    } else {
      destination = lockAccount(destinationAccountId);
      source = lockAccount(sourceAccountId);
    }

    validateAccountIsActive(source);
    validateAccountIsActive(destination);

    BigDecimal sourceBalanceBefore = source.getBalance();
    BigDecimal sourceBalanceAfter = balanceAfterTakingOut(source, request.getAmount(), "Transfer");
    BigDecimal destinationBalanceBefore = destination.getBalance();
    BigDecimal destinationBalanceAfter = destinationBalanceBefore.add(request.getAmount());

    source.setBalance(sourceBalanceAfter);
    source.setAvailableBalance(sourceBalanceAfter);
    savingsAccountRepository.save(source);

    destination.setBalance(destinationBalanceAfter);
    destination.setAvailableBalance(destinationBalanceAfter);
    savingsAccountRepository.save(destination);

    saveTransaction(
        source,
        TransactionType.TRANSFER_OUT,
        request.getAmount(),
        sourceBalanceBefore,
        sourceBalanceAfter,
        "Transfer to " + destination.getAccountNumber(),
        null);
    saveTransaction(
        destination,
        TransactionType.TRANSFER_IN,
        request.getAmount(),
        destinationBalanceBefore,
        destinationBalanceAfter,
        "Transfer from " + source.getAccountNumber(),
        null);
    log.info("Transfer successful. New balance of source account: {}", sourceBalanceAfter);

    return TransferResponse.builder()
        .sourceAccountId(sourceAccountId)
        .destinationAccountId(destinationAccountId)
        .destinationAccountNumber(destination.getAccountNumber())
        .amount(request.getAmount())
        .balanceBefore(sourceBalanceBefore)
        .newBalance(sourceBalanceAfter)
        .transactionDate(LocalDateTime.now())
        .build();
  }

  /*
   * Credits a matured fixed deposit (principal plus interest) to the account.
   * The fixed deposit service retries until it gets an answer, so a reference
   * that was already credited returns the earlier result instead of paying
   * twice. The account row lock makes concurrent retries wait their turn.
   */
  @Transactional
  public TransactionResponse creditFixedDepositPayout(
      Long accountId, FixedDepositPayoutRequest request) {
    log.info(
        "Processing fixed deposit payout {} into account ID: {}",
        request.getReference(),
        accountId);

    SavingsAccount account = lockAccount(accountId);

    Optional<SavingsTransaction> alreadyCredited =
        savingsTransactionRepository.findByReference(request.getReference());
    if (alreadyCredited.isPresent()) {
      SavingsTransaction earlier = alreadyCredited.get();
      if (!earlier.getAccount().getId().equals(accountId)) {
        throw new PayoutReferenceConflictException(
            "Payout " + request.getReference() + " was already credited to another account");
      }
      log.info("Fixed deposit payout {} was already credited", request.getReference());
      return toTransactionResponse(accountId, earlier);
    }

    validateAccountIsActive(account);

    BigDecimal amount = request.getPrincipal().add(request.getInterest());
    BigDecimal balanceBefore = account.getBalance();
    BigDecimal newBalance = balanceBefore.add(amount);
    account.setBalance(newBalance);
    account.setAvailableBalance(newBalance);
    savingsAccountRepository.save(account);

    String description = "Fixed deposit payout - " + request.getReference();
    SavingsTransaction transaction =
        saveTransaction(
            account,
            TransactionType.FIXED_DEPOSIT_PAYOUT,
            amount,
            balanceBefore,
            newBalance,
            description,
            request.getReference());

    // Principal and interest come from different places in the books, so
    // they are posted separately.
    ledgerPostingService.record(
        LEDGER_FD_PAYOUT,
        request.getPrincipal(),
        request.getReference(),
        "Fixed deposit payout (principal) - " + request.getReference());
    if (request.getInterest().compareTo(BigDecimal.ZERO) > 0) {
      ledgerPostingService.record(
          LEDGER_FD_INTEREST_PAYOUT,
          request.getInterest(),
          request.getReference() + "-INT",
          "Fixed deposit payout (interest) - " + request.getReference());
    }
    log.info(
        "Fixed deposit payout {} credited. New balance: {}", request.getReference(), newBalance);

    return toTransactionResponse(accountId, transaction);
  }

  public List<SavingsAccountResponse> getAccountsByClientId(Long clientId) {
    log.info("Fetching savings accounts for client ID: {}", clientId);

    return savingsAccountRepository.findByClientId(clientId).stream()
        .map(SavingsAccountMapper::toResponse)
        .collect(Collectors.toList());
  }

  public SavingsAccountResponse getAccountById(Long accountId) {
    log.info("Fetching savings account with ID: {}", accountId);

    SavingsAccount account =
        savingsAccountRepository
            .findById(accountId)
            .orElseThrow(
                () ->
                    new AccountNotFoundException(
                        "Savings account not found with ID: " + accountId));

    return SavingsAccountMapper.toResponse(account);
  }

  /* Most recent first. */
  @Transactional(readOnly = true)
  public PageResponse<SavingsTransactionResponse> getTransactionHistory(
      Long accountId, int page, int size) {
    log.info("Fetching transaction history for account ID: {}", accountId);

    savingsAccountRepository
        .findById(accountId)
        .orElseThrow(
            () -> new AccountNotFoundException("Savings account not found with ID: " + accountId));

    Page<SavingsTransaction> transactions =
        savingsTransactionRepository.findByAccountId(
            accountId,
            Paging.request(page, size, Sort.by(Sort.Direction.DESC, "transactionDate", "id")));
    return PageResponse.of(
        transactions, transactions.map(SavingsTransactionMapper::toResponse).getContent());
  }

  private void validateAccountIsActive(SavingsAccount account) {
    if (account.getStatus() != SavingsAccountStatus.ACTIVE) {
      throw new AccountNotActiveException(
          "Account "
              + account.getAccountNumber()
              + " is not active. Current status: "
              + account.getStatus());
    }
  }

  private TransactionResponse toTransactionResponse(
      Long accountId, SavingsTransaction transaction) {
    return TransactionResponse.builder()
        .accountId(accountId)
        .transactionType(transaction.getTransactionType().name())
        .amount(transaction.getAmount())
        .balanceBefore(transaction.getBalanceBefore())
        .newBalance(transaction.getBalanceAfter())
        .transactionDate(transaction.getTransactionDate())
        .build();
  }

  private SavingsAccount lockAccount(Long accountId) {
    return savingsAccountRepository
        .findByIdForUpdate(accountId)
        .orElseThrow(
            () -> new AccountNotFoundException("Savings account not found with ID: " + accountId));
  }

  /*
   * Returns the balance the account would have after taking the amount out,
   * or refuses if that would overdraw it or breach its minimum balance.
   */
  private BigDecimal balanceAfterTakingOut(
      SavingsAccount account, BigDecimal amount, String operation) {
    BigDecimal balanceAfter = account.getBalance().subtract(amount);

    if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
      throw new InsufficientBalanceException(
          operation
              + " of "
              + amount
              + " exceeds the available balance of "
              + account.getBalance());
    }

    if (account.getProduct().isEnforceMinimumBalance()
        && balanceAfter.compareTo(account.getProduct().getMinimumBalance()) < 0) {
      throw new InsufficientBalanceException(
          operation
              + " of "
              + amount
              + " would breach the minimum balance of "
              + account.getProduct().getMinimumBalance());
    }

    return balanceAfter;
  }

  /*
   * Saves the transaction row and queues its ledger entry. Both are written
   * in the caller's transaction, so they commit together with the new balance.
   */
  private void recordTransaction(
      SavingsAccount account,
      TransactionType type,
      String ledgerType,
      BigDecimal amount,
      BigDecimal balanceBefore,
      BigDecimal balanceAfter,
      String description) {
    SavingsTransaction savedTransaction =
        saveTransaction(account, type, amount, balanceBefore, balanceAfter, description, null);

    String reference = account.getAccountNumber() + "-" + savedTransaction.getId();
    ledgerPostingService.record(ledgerType, amount, reference, description + " - " + reference);
  }

  private SavingsTransaction saveTransaction(
      SavingsAccount account,
      TransactionType type,
      BigDecimal amount,
      BigDecimal balanceBefore,
      BigDecimal balanceAfter,
      String description,
      String reference) {
    SavingsTransaction transaction = new SavingsTransaction();
    transaction.setAccount(account);
    transaction.setTransactionType(type);
    transaction.setAmount(amount);
    transaction.setBalanceBefore(balanceBefore);
    transaction.setBalanceAfter(balanceAfter);
    transaction.setDescription(description);
    transaction.setReference(reference);
    return savingsTransactionRepository.save(transaction);
  }

  private String generateAccountNumber() {
    String accountNumber;
    do {
      accountNumber = String.format("SAV%010d", RANDOM.nextLong(10_000_000_000L));
    } while (savingsAccountRepository.existsByAccountNumber(accountNumber));
    return accountNumber;
  }
}
