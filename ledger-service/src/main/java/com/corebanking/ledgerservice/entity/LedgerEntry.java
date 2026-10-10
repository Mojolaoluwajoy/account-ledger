package com.corebanking.ledgerservice.entity;

import com.corebanking.ledgerservice.enums.TransactionEntryType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true)
  private String transactionReference;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TransactionEntryType transactionType;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "debit_account_id", nullable = false)
  private GlAccount debitAccount;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "credit_account_id", nullable = false)
  private GlAccount creditAccount;

  @Column(nullable = false)
  private BigDecimal amount;

  private String description;

  @Column(nullable = false, updatable = false)
  private LocalDateTime entryDate = LocalDateTime.now();
}
