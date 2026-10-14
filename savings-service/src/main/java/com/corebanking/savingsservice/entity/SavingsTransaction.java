package com.corebanking.savingsservice.entity;

import com.corebanking.savingsservice.enums.TransactionType;
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
@Table(name = "savings_transactions")
public class SavingsTransaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /*
   * The account this transaction belongs to.
   * Many transactions can belong to one account.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "account_id", nullable = false)
  private SavingsAccount account;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TransactionType transactionType;

  @Column(nullable = false)
  private BigDecimal amount;

  @Column(nullable = false)
  private BigDecimal balanceBefore;

  @Column(nullable = false)
  private BigDecimal balanceAfter;

  private String description;

  /*
   * Set when the money came from another service (a fixed deposit payout). It
   * is unique, so the same payout can never be credited twice.
   */
  @Column(unique = true)
  private String reference;

  @Column(nullable = false, updatable = false)
  private LocalDateTime transactionDate = LocalDateTime.now();
}
