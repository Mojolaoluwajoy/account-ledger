package com.accountledger.savingsservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.accountledger.savingsservice.dto.FixedDepositPayoutRequest;
import com.accountledger.savingsservice.dto.OpenSavingsAccountRequest;
import com.accountledger.savingsservice.dto.SavingsAccountResponse;
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
import com.accountledger.savingsservice.exception.ClientNotFoundException;
import com.accountledger.savingsservice.exception.InsufficientBalanceException;
import com.accountledger.savingsservice.exception.InvalidTransferException;
import com.accountledger.savingsservice.exception.PayoutReferenceConflictException;
import com.accountledger.savingsservice.exception.ProductNotFoundException;
import com.accountledger.savingsservice.repository.SavingsAccountRepository;
import com.accountledger.savingsservice.repository.SavingsProductRepository;
import com.accountledger.savingsservice.repository.SavingsTransactionRepository;
import com.accountledger.savingsservice.service.InterestService;
import com.accountledger.savingsservice.service.LedgerPostingService;
import com.accountledger.savingsservice.service.SavingsAccountService;
import com.accountledger.savingsservice.util.ClientServiceClient;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsAccountServiceTest {

  @Mock private SavingsAccountRepository savingsAccountRepository;

  @Mock private SavingsProductRepository savingsProductRepository;

  @Mock private SavingsTransactionRepository savingsTransactionRepository;

  @Mock private LedgerPostingService ledgerPostingService;

  @Mock private ClientServiceClient clientServiceClient;

  @Mock private InterestService interestService;

  @InjectMocks private SavingsAccountService savingsAccountService;

  private SavingsProduct product;
  private SavingsAccount account;
  private OpenSavingsAccountRequest openRequest;

  @BeforeEach
  void setUp() {
    product = new SavingsProduct();
    product.setId(1L);
    product.setName("Regular Savings");
    product.setShortName("REGSAV");
    product.setNominalAnnualInterestRate(new BigDecimal("5.00"));
    product.setMinimumOpeningBalance(new BigDecimal("100.00"));
    product.setMinimumBalance(new BigDecimal("50.00"));
    product.setEnforceMinimumBalance(true);

    account = new SavingsAccount();
    account.setId(1L);
    account.setClientId(1L);
    account.setProduct(product);
    account.setAccountNumber("SAV000000001");
    account.setBalance(new BigDecimal("500.00"));
    account.setAvailableBalance(new BigDecimal("500.00"));
    account.setStatus(SavingsAccountStatus.ACTIVE);

    openRequest = new OpenSavingsAccountRequest();
    openRequest.setClientId(1L);
    openRequest.setProductId(1L);
    openRequest.setInitialDeposit(new BigDecimal("200.00"));

    lenient()
        .when(savingsTransactionRepository.save(any(SavingsTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void openAccount_withValidRequest_shouldReturnSavingsAccountResponse() {
    when(savingsProductRepository.findById(1L)).thenReturn(Optional.of(product));
    when(savingsAccountRepository.save(any(SavingsAccount.class))).thenReturn(account);

    SavingsAccountResponse response = savingsAccountService.openAccount(openRequest);

    assertNotNull(response);
    assertEquals("SAV000000001", response.getAccountNumber());
    assertEquals(SavingsAccountStatus.ACTIVE, response.getStatus());
    verify(savingsAccountRepository, times(1)).save(any(SavingsAccount.class));
    verify(savingsTransactionRepository, times(1)).save(any(SavingsTransaction.class));
    verify(ledgerPostingService)
        .record(eq("SAVINGS_DEPOSIT"), eq(new BigDecimal("200.00")), anyString(), anyString());
  }

  @Test
  void openAccount_withUnknownClient_shouldThrowException() {
    doThrow(new ClientNotFoundException("Client not found with ID: 1"))
        .when(clientServiceClient)
        .requireActiveClient(1L);

    assertThrows(
        ClientNotFoundException.class, () -> savingsAccountService.openAccount(openRequest));

    verify(savingsAccountRepository, never()).save(any(SavingsAccount.class));
  }

  @Test
  void openAccount_withInvalidProductId_shouldThrowException() {
    when(savingsProductRepository.findById(99L)).thenReturn(Optional.empty());
    openRequest.setProductId(99L);

    assertThrows(
        ProductNotFoundException.class, () -> savingsAccountService.openAccount(openRequest));

    verify(savingsAccountRepository, never()).save(any(SavingsAccount.class));
  }

  @Test
  void deposit_withValidAmount_shouldIncreaseBalance() {
    TransactionRequest request = new TransactionRequest();
    request.setAmount(new BigDecimal("200.00"));

    when(savingsAccountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
    when(savingsAccountRepository.save(any(SavingsAccount.class))).thenReturn(account);

    TransactionResponse response = savingsAccountService.deposit(1L, request);

    assertNotNull(response);
    assertEquals(new BigDecimal("700.00"), response.getNewBalance());
    verify(ledgerPostingService)
        .record(eq("SAVINGS_DEPOSIT"), eq(new BigDecimal("200.00")), anyString(), anyString());
  }

  @Test
  void withdraw_belowMinimumBalance_shouldThrowException() {
    TransactionRequest request = new TransactionRequest();
    request.setAmount(new BigDecimal("460.00"));

    when(savingsAccountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));

    assertThrows(
        InsufficientBalanceException.class, () -> savingsAccountService.withdraw(1L, request));

    verify(savingsAccountRepository, never()).save(any(SavingsAccount.class));
  }

  @Test
  void withdraw_moreThanBalance_withoutMinimumBalanceRule_shouldThrowException() {
    product.setEnforceMinimumBalance(false);
    TransactionRequest request = new TransactionRequest();
    request.setAmount(new BigDecimal("500.01"));

    when(savingsAccountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));

    assertThrows(
        InsufficientBalanceException.class, () -> savingsAccountService.withdraw(1L, request));

    verify(savingsAccountRepository, never()).save(any(SavingsAccount.class));
  }

  @Test
  void withdraw_entireBalance_withoutMinimumBalanceRule_shouldLeaveZero() {
    product.setEnforceMinimumBalance(false);
    TransactionRequest request = new TransactionRequest();
    request.setAmount(new BigDecimal("500.00"));

    when(savingsAccountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));

    TransactionResponse response = savingsAccountService.withdraw(1L, request);

    assertEquals(new BigDecimal("0.00"), response.getNewBalance());
  }

  @Test
  void deposit_withInvalidAccountId_shouldThrowException() {
    TransactionRequest request = new TransactionRequest();
    request.setAmount(new BigDecimal("100.00"));

    when(savingsAccountRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

    assertThrows(AccountNotFoundException.class, () -> savingsAccountService.deposit(99L, request));
  }

  @Test
  void transfer_withValidAmount_shouldMoveMoneyBetweenAccounts() {
    SavingsAccount destination = destinationAccount();
    when(savingsAccountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
    when(savingsAccountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(destination));

    TransferResponse response = savingsAccountService.transfer(1L, transferOf("200.00", 2L));

    assertEquals(new BigDecimal("300.00"), response.getNewBalance());
    assertEquals(new BigDecimal("300.00"), account.getBalance());
    assertEquals(new BigDecimal("300.00"), destination.getBalance());
    verify(savingsTransactionRepository, times(2)).save(any(SavingsTransaction.class));
    // Both accounts sit under the same control account, so the ledger is not touched.
    verifyNoInteractions(ledgerPostingService);
  }

  @Test
  void transfer_toSameAccount_shouldThrowException() {
    assertThrows(
        InvalidTransferException.class,
        () -> savingsAccountService.transfer(1L, transferOf("200.00", 1L)));

    verify(savingsAccountRepository, never()).save(any(SavingsAccount.class));
  }

  @Test
  void transfer_belowMinimumBalance_shouldThrowException() {
    SavingsAccount destination = destinationAccount();
    when(savingsAccountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
    when(savingsAccountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(destination));

    assertThrows(
        InsufficientBalanceException.class,
        () -> savingsAccountService.transfer(1L, transferOf("460.00", 2L)));

    assertEquals(new BigDecimal("100.00"), destination.getBalance());
    verify(savingsAccountRepository, never()).save(any(SavingsAccount.class));
  }

  @Test
  void transfer_toInactiveAccount_shouldThrowException() {
    SavingsAccount destination = destinationAccount();
    destination.setStatus(SavingsAccountStatus.CLOSED);
    when(savingsAccountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
    when(savingsAccountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(destination));

    assertThrows(
        AccountNotActiveException.class,
        () -> savingsAccountService.transfer(1L, transferOf("200.00", 2L)));

    verify(savingsAccountRepository, never()).save(any(SavingsAccount.class));
  }

  @Test
  void transfer_toUnknownAccount_shouldThrowException() {
    when(savingsAccountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
    when(savingsAccountRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

    assertThrows(
        AccountNotFoundException.class,
        () -> savingsAccountService.transfer(1L, transferOf("200.00", 99L)));
  }

  private SavingsAccount destinationAccount() {
    SavingsAccount destination = new SavingsAccount();
    destination.setId(2L);
    destination.setClientId(2L);
    destination.setProduct(product);
    destination.setAccountNumber("SAV000000002");
    destination.setBalance(new BigDecimal("100.00"));
    destination.setAvailableBalance(new BigDecimal("100.00"));
    destination.setStatus(SavingsAccountStatus.ACTIVE);
    return destination;
  }

  private TransferRequest transferOf(String amount, Long destinationAccountId) {
    TransferRequest request = new TransferRequest();
    request.setAmount(new BigDecimal(amount));
    request.setDestinationAccountId(destinationAccountId);
    return request;
  }

  @Test
  void creditFixedDepositPayout_shouldCreditPrincipalAndInterestAndPostBoth() {
    when(savingsAccountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
    when(savingsTransactionRepository.findByReference("FD-PAYOUT-7")).thenReturn(Optional.empty());

    TransactionResponse response =
        savingsAccountService.creditFixedDepositPayout(1L, payoutOf("10000.00", "246.58"));

    assertEquals(new BigDecimal("10746.58"), response.getNewBalance());
    assertEquals(new BigDecimal("10746.58"), account.getBalance());
    verify(ledgerPostingService)
        .record(
            eq("FIXED_DEPOSIT_PAYOUT"),
            eq(new BigDecimal("10000.00")),
            eq("FD-PAYOUT-7"),
            anyString());
    verify(ledgerPostingService)
        .record(
            eq("FIXED_DEPOSIT_INTEREST_PAYOUT"),
            eq(new BigDecimal("246.58")),
            eq("FD-PAYOUT-7-INT"),
            anyString());
  }

  @Test
  void creditFixedDepositPayout_alreadyCredited_shouldReturnEarlierResultWithoutPayingAgain() {
    SavingsTransaction earlier = new SavingsTransaction();
    earlier.setAccount(account);
    earlier.setTransactionType(TransactionType.FIXED_DEPOSIT_PAYOUT);
    earlier.setAmount(new BigDecimal("10246.58"));
    earlier.setBalanceBefore(new BigDecimal("500.00"));
    earlier.setBalanceAfter(new BigDecimal("10746.58"));
    earlier.setReference("FD-PAYOUT-7");
    when(savingsAccountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
    when(savingsTransactionRepository.findByReference("FD-PAYOUT-7"))
        .thenReturn(Optional.of(earlier));

    TransactionResponse response =
        savingsAccountService.creditFixedDepositPayout(1L, payoutOf("10000.00", "246.58"));

    assertEquals(new BigDecimal("10746.58"), response.getNewBalance());
    assertEquals(new BigDecimal("500.00"), account.getBalance());
    verify(savingsAccountRepository, never()).save(any(SavingsAccount.class));
    verifyNoInteractions(ledgerPostingService);
  }

  @Test
  void creditFixedDepositPayout_referenceUsedOnAnotherAccount_shouldThrowException() {
    SavingsTransaction earlier = new SavingsTransaction();
    earlier.setAccount(destinationAccount());
    earlier.setReference("FD-PAYOUT-7");
    when(savingsAccountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
    when(savingsTransactionRepository.findByReference("FD-PAYOUT-7"))
        .thenReturn(Optional.of(earlier));

    assertThrows(
        PayoutReferenceConflictException.class,
        () -> savingsAccountService.creditFixedDepositPayout(1L, payoutOf("10000.00", "246.58")));

    verifyNoInteractions(ledgerPostingService);
  }

  @Test
  void creditFixedDepositPayout_toInactiveAccount_shouldThrowException() {
    account.setStatus(SavingsAccountStatus.CLOSED);
    when(savingsAccountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
    when(savingsTransactionRepository.findByReference("FD-PAYOUT-7")).thenReturn(Optional.empty());

    assertThrows(
        AccountNotActiveException.class,
        () -> savingsAccountService.creditFixedDepositPayout(1L, payoutOf("10000.00", "246.58")));

    verifyNoInteractions(ledgerPostingService);
  }

  private FixedDepositPayoutRequest payoutOf(String principal, String interest) {
    FixedDepositPayoutRequest request = new FixedDepositPayoutRequest();
    request.setReference("FD-PAYOUT-7");
    request.setPrincipal(new BigDecimal(principal));
    request.setInterest(new BigDecimal(interest));
    return request;
  }
}
