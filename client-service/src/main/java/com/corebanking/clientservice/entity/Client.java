package com.corebanking.clientservice.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "clients")
public class Client {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column private Long fineractClientId;

  /* True while a local change to this client has not yet reached Fineract. */
  @Column(nullable = false)
  private boolean fineractSyncPending = false;

  /* Why the last attempt to reach Fineract failed; null once it succeeds. */
  @Column(length = 500)
  private String fineractSyncError;

  @Column(nullable = false)
  private String firstName;

  @Column(nullable = false)
  private String lastName;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false, unique = true)
  private String phoneNumber;

  private LocalDate dateOfBirth;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ClientStatus status = ClientStatus.PENDING;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  /*
   * Bumped by Hibernate on every save. A save based on an older copy of the
   * client fails instead of silently overwriting the newer change.
   */
  @Version
  @Column(nullable = false)
  private Long version;
}
