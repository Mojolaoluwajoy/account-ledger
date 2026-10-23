package com.accountledger.ledgerservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.accountledger.ledgerservice.dto.LedgerEntryResponse;
import com.accountledger.ledgerservice.dto.PostLedgerEntryRequest;
import com.accountledger.ledgerservice.entity.GlAccount;
import com.accountledger.ledgerservice.entity.LedgerEntry;
import com.accountledger.ledgerservice.enums.GlAccountType;
import com.accountledger.ledgerservice.enums.TransactionEntryType;
import com.accountledger.ledgerservice.exception.GlAccountNotFoundException;
import com.accountledger.ledgerservice.exception.ReferenceConflictException;
import com.accountledger.ledgerservice.repository.GlAccountRepository;
import com.accountledger.ledgerservice.repository.LedgerEntryRepository;
import com.accountledger.ledgerservice.service.LedgerService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

  @Mock private LedgerEntryRepository ledgerEntryRepository;

  @Mock private GlAccountRepository glAccountRepository;

  @InjectMocks private LedgerService ledgerService;

  private GlAccount cashAccount;
  private GlAccount savingsControlAccount;
  private GlAccount fixedDepositLiabilityAccount;
  private LedgerEntry savedEntry;
  private PostLedgerEntryRequest depositRequest;
  private PostLedgerEntryRequest withdrawalRequest;
  private PostLedgerEntryRequest fdCreationRequest;

  @BeforeEach
  void setUp() {

    cashAccount = new GlAccount();
    cashAccount.setId(1L);
    cashAccount.setAccountCode("1001");
    cashAccount.setAccountName("Cash");
    cashAccount.setAccountType(GlAccountType.ASSET);

    savingsControlAccount = new GlAccount();
    savingsControlAccount.setId(2L);
    savingsControlAccount.setAccountCode("2001");
    savingsControlAccount.setAccountName("Savings Control");
    savingsControlAccount.setAccountType(GlAccountType.LIABILITY);

    fixedDepositLiabilityAccount = new GlAccount();
    fixedDepositLiabilityAccount.setId(3L);
    fixedDepositLiabilityAccount.setAccountCode("2002");
    fixedDepositLiabilityAccount.setAccountName("Fixed Deposit Liability");
    fixedDepositLiabilityAccount.setAccountType(GlAccountType.LIABILITY);

    savedEntry = new LedgerEntry();
    savedEntry.setId(1L);
    savedEntry.setDebitAccount(cashAccount);
    savedEntry.setCreditAccount(savingsControlAccount);
    savedEntry.setAmount(new BigDecimal("500.00"));
    savedEntry.setTransactionType(TransactionEntryType.SAVINGS_DEPOSIT);
    savedEntry.setTransactionReference("SAV001-123");

    depositRequest = new PostLedgerEntryRequest();
    depositRequest.setTransactionType(TransactionEntryType.SAVINGS_DEPOSIT);
    depositRequest.setAmount(new BigDecimal("500.00"));
    depositRequest.setTransactionReference("SAV001-123");
    depositRequest.setDescription("Savings deposit");

    withdrawalRequest = new PostLedgerEntryRequest();
    withdrawalRequest.setTransactionType(TransactionEntryType.SAVINGS_WITHDRAWAL);
    withdrawalRequest.setAmount(new BigDecimal("200.00"));
    withdrawalRequest.setTransactionReference("SAV001-456");
    withdrawalRequest.setDescription("Savings withdrawal");

    fdCreationRequest = new PostLedgerEntryRequest();
    fdCreationRequest.setTransactionType(TransactionEntryType.FIXED_DEPOSIT_CREATION);
    fdCreationRequest.setAmount(new BigDecimal("10000.00"));
    fdCreationRequest.setTransactionReference("FD-1-123");
    fdCreationRequest.setDescription("Fixed deposit creation");
  }

  @Test
  void postEntry_savingsDeposit_shouldDebitCashAndCreditSavingsControl() {
    when(glAccountRepository.findByAccountCode("1001")).thenReturn(Optional.of(cashAccount));
    when(glAccountRepository.findByAccountCode("2001"))
        .thenReturn(Optional.of(savingsControlAccount));
    when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenReturn(savedEntry);

    LedgerEntryResponse response = ledgerService.postEntry(depositRequest);

    assertNotNull(response);
    assertEquals("1001", response.getDebitAccountCode());
    assertEquals("2001", response.getCreditAccountCode());
    assertEquals(new BigDecimal("500.00"), response.getAmount());
    verify(ledgerEntryRepository, times(1)).save(any(LedgerEntry.class));
  }

  @Test
  void postEntry_savingsWithdrawal_shouldDebitSavingsControlAndCreditCash() {
    LedgerEntry withdrawalEntry = new LedgerEntry();
    withdrawalEntry.setId(2L);
    withdrawalEntry.setDebitAccount(savingsControlAccount);
    withdrawalEntry.setCreditAccount(cashAccount);
    withdrawalEntry.setAmount(new BigDecimal("200.00"));
    withdrawalEntry.setTransactionType(TransactionEntryType.SAVINGS_WITHDRAWAL);
    withdrawalEntry.setTransactionReference("SAV001-456");

    when(glAccountRepository.findByAccountCode("2001"))
        .thenReturn(Optional.of(savingsControlAccount));
    when(glAccountRepository.findByAccountCode("1001")).thenReturn(Optional.of(cashAccount));
    when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenReturn(withdrawalEntry);

    LedgerEntryResponse response = ledgerService.postEntry(withdrawalRequest);

    assertNotNull(response);
    assertEquals("2001", response.getDebitAccountCode());
    assertEquals("1001", response.getCreditAccountCode());
    assertEquals(new BigDecimal("200.00"), response.getAmount());
  }

  @Test
  void postEntry_fixedDepositCreation_shouldDebitCashAndCreditFixedDepositLiability() {
    LedgerEntry fdEntry = new LedgerEntry();
    fdEntry.setId(3L);
    fdEntry.setDebitAccount(cashAccount);
    fdEntry.setCreditAccount(fixedDepositLiabilityAccount);
    fdEntry.setAmount(new BigDecimal("10000.00"));
    fdEntry.setTransactionType(TransactionEntryType.FIXED_DEPOSIT_CREATION);
    fdEntry.setTransactionReference("FD-1-123");

    when(glAccountRepository.findByAccountCode("1001")).thenReturn(Optional.of(cashAccount));
    when(glAccountRepository.findByAccountCode("2002"))
        .thenReturn(Optional.of(fixedDepositLiabilityAccount));
    when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenReturn(fdEntry);

    LedgerEntryResponse response = ledgerService.postEntry(fdCreationRequest);

    assertNotNull(response);
    assertEquals("1001", response.getDebitAccountCode());
    assertEquals("2002", response.getCreditAccountCode());
    assertEquals(new BigDecimal("10000.00"), response.getAmount());
  }

  @Test
  void postEntry_fixedDepositInterest_shouldDebitInterestExpenseAndCreditCash() {
    GlAccount interestExpenseAccount = new GlAccount();
    interestExpenseAccount.setId(4L);
    interestExpenseAccount.setAccountCode("5001");
    interestExpenseAccount.setAccountName("Interest Expense");
    interestExpenseAccount.setAccountType(GlAccountType.EXPENSE);

    PostLedgerEntryRequest interestRequest = new PostLedgerEntryRequest();
    interestRequest.setTransactionType(TransactionEntryType.FIXED_DEPOSIT_INTEREST);
    interestRequest.setAmount(new BigDecimal("591.78"));
    interestRequest.setTransactionReference("FD-INT-1");

    when(glAccountRepository.findByAccountCode("5001"))
        .thenReturn(Optional.of(interestExpenseAccount));
    when(glAccountRepository.findByAccountCode("1001")).thenReturn(Optional.of(cashAccount));
    when(ledgerEntryRepository.save(any(LedgerEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    LedgerEntryResponse response = ledgerService.postEntry(interestRequest);

    assertEquals("5001", response.getDebitAccountCode());
    assertEquals("1001", response.getCreditAccountCode());
    assertEquals(new BigDecimal("591.78"), response.getAmount());
  }

  @Test
  void postEntry_fixedDepositPayout_shouldDebitFixedDepositLiabilityAndCreditSavings() {
    LedgerEntryResponse response =
        postPayoutEntry(TransactionEntryType.FIXED_DEPOSIT_PAYOUT, "2002", "FD-PAYOUT-1");

    assertEquals("2002", response.getDebitAccountCode());
    assertEquals("2001", response.getCreditAccountCode());
  }

  @Test
  void postEntry_fixedDepositInterestPayout_shouldDebitInterestExpenseAndCreditSavings() {
    LedgerEntryResponse response =
        postPayoutEntry(
            TransactionEntryType.FIXED_DEPOSIT_INTEREST_PAYOUT, "5001", "FD-PAYOUT-1-INT");

    assertEquals("5001", response.getDebitAccountCode());
    assertEquals("2001", response.getCreditAccountCode());
  }

  @Test
  void postEntry_savingsInterest_shouldDebitInterestExpenseAndCreditSavings() {
    LedgerEntryResponse response =
        postPayoutEntry(TransactionEntryType.SAVINGS_INTEREST, "5001", "INT-SAV1-2026-09");

    assertEquals("5001", response.getDebitAccountCode());
    assertEquals("2001", response.getCreditAccountCode());
  }

  private LedgerEntryResponse postPayoutEntry(
      TransactionEntryType type, String debitCode, String reference) {
    GlAccount debitAccount = new GlAccount();
    debitAccount.setId(10L);
    debitAccount.setAccountCode(debitCode);
    debitAccount.setAccountName("Debit side");
    debitAccount.setAccountType(GlAccountType.LIABILITY);

    GlAccount savingsAccount = new GlAccount();
    savingsAccount.setId(11L);
    savingsAccount.setAccountCode("2001");
    savingsAccount.setAccountName("Savings Control");
    savingsAccount.setAccountType(GlAccountType.LIABILITY);

    PostLedgerEntryRequest request = new PostLedgerEntryRequest();
    request.setTransactionType(type);
    request.setAmount(new BigDecimal("100.00"));
    request.setTransactionReference(reference);

    when(glAccountRepository.findByAccountCode(debitCode)).thenReturn(Optional.of(debitAccount));
    when(glAccountRepository.findByAccountCode("2001")).thenReturn(Optional.of(savingsAccount));
    when(ledgerEntryRepository.save(any(LedgerEntry.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    return ledgerService.postEntry(request);
  }

  @Test
  void postEntry_withAlreadyPostedReference_shouldReturnExistingEntryWithoutSavingAgain() {
    when(ledgerEntryRepository.findFirstByTransactionReference("SAV001-123"))
        .thenReturn(Optional.of(savedEntry));

    LedgerEntryResponse response = ledgerService.postEntry(depositRequest);

    assertEquals(1L, response.getId());
    verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
  }

  @Test
  void postEntry_withReferenceAlreadyPostedForDifferentAmount_shouldRefuse() {
    when(ledgerEntryRepository.findFirstByTransactionReference("SAV001-123"))
        .thenReturn(Optional.of(savedEntry));
    depositRequest.setAmount(new BigDecimal("600.00"));

    assertThrows(ReferenceConflictException.class, () -> ledgerService.postEntry(depositRequest));

    verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
  }

  @Test
  void postEntry_withReferenceAlreadyPostedForDifferentType_shouldRefuse() {
    when(ledgerEntryRepository.findFirstByTransactionReference("SAV001-123"))
        .thenReturn(Optional.of(savedEntry));
    depositRequest.setTransactionType(TransactionEntryType.SAVINGS_WITHDRAWAL);

    assertThrows(ReferenceConflictException.class, () -> ledgerService.postEntry(depositRequest));

    verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
  }

  @Test
  void postEntry_retryWithSameAmountAtDifferentScale_shouldReturnExistingEntry() {
    when(ledgerEntryRepository.findFirstByTransactionReference("SAV001-123"))
        .thenReturn(Optional.of(savedEntry));
    depositRequest.setAmount(new BigDecimal("500"));

    assertEquals(1L, ledgerService.postEntry(depositRequest).getId());
  }

  @Test
  void postEntry_withMissingGlAccount_shouldThrowGlAccountNotFoundException() {
    when(glAccountRepository.findByAccountCode("1001")).thenReturn(Optional.empty());

    assertThrows(GlAccountNotFoundException.class, () -> ledgerService.postEntry(depositRequest));

    verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
  }
}
