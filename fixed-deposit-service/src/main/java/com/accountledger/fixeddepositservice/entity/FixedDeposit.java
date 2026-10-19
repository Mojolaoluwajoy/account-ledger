package com.accountledger.fixeddepositservice.entity;

import com.accountledger.fixeddepositservice.enums.FixedDepositStatus;
import com.accountledger.fixeddepositservice.enums.PayoutStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "fixed_deposits")
public class FixedDeposit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long clientId;

  @Column(nullable = false)
  private BigDecimal principalAmount;

  @Column(nullable = false)
  private BigDecimal interestRate;

  @Column(nullable = false)
  private Integer tenureInDays;

  @Column(nullable = false)
  private LocalDate depositDate;

  @Column(nullable = false)
  private LocalDate maturityDate;

  @Column(nullable = false)
  private BigDecimal maturityAmount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private FixedDepositStatus status = FixedDepositStatus.ACTIVE;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  /*
   * The savings account that receives principal and interest at maturity.
   * Empty only for deposits created before payouts existed; those are paid
   * out in cash as before.
   */
  private Long payoutAccountId;

  /* Empty until the deposit matures. */
  @Enumerated(EnumType.STRING)
  private PayoutStatus payoutStatus;

  @Column(nullable = false)
  private int payoutAttempts = 0;

  private String payoutError;

  private LocalDateTime paidOutAt;
}
