package com.accountledger.ledgerservice.entity;

import com.accountledger.ledgerservice.enums.GlAccountType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "gl_accounts")
public class GlAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String accountCode;

  @Column(nullable = false)
  private String accountName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private GlAccountType accountType;

  private String description;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
}
