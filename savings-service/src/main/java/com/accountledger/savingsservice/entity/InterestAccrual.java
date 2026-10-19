package com.accountledger.savingsservice.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * Interest one account earned on one day, kept to 6 decimal places. A month's
 * rows are added up, rounded to 2 places and paid into the account in one
 * posting; postingReference is set once a row has been paid.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "interest_accruals")
public class InterestAccrual {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long accountId;

  @Column(nullable = false)
  private LocalDate accrualDate;

  /* The account's balance at the end of accrualDate. */
  @Column(nullable = false)
  private BigDecimal balance;

  @Column(nullable = false)
  private BigDecimal annualRate;

  @Column(nullable = false, precision = 38, scale = 6)
  private BigDecimal amount;

  private String postingReference;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
}
