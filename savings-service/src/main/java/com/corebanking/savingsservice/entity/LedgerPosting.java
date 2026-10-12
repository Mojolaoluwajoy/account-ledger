package com.corebanking.savingsservice.entity;

import com.corebanking.savingsservice.enums.LedgerPostingStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * A ledger entry we still owe the ledger service.
 *
 * It is saved in the same database transaction as the business change it
 * belongs to, so the two can never disagree. A background job then sends it
 * to the ledger service and keeps retrying until it is accepted. If the
 * ledger service rejects it outright it is marked FAILED instead, with the
 * reason in lastError.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ledger_postings")
public class LedgerPosting {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String transactionType;

  @Column(nullable = false)
  private BigDecimal amount;

  @Column(nullable = false, unique = true)
  private String transactionReference;

  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private LedgerPostingStatus status = LedgerPostingStatus.PENDING;

  @Column(nullable = false)
  private int attempts = 0;

  private String lastError;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  private LocalDateTime postedAt;
}
