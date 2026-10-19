package com.accountledger.clientservice.dto;

import com.accountledger.clientservice.entity.ClientStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClientResponse {

  private Long id;
  @Builder.Default private Long fineractClientId = null;
  private String firstName;
  private String lastName;
  private String email;
  private String phoneNumber;
  private LocalDate dateOfBirth;

  private ClientStatus status;
  private boolean fineractSyncPending;
  private String fineractSyncError;
  private LocalDateTime createdAt;

  /* Send this back when updating the client to be told if someone changed it meanwhile. */
  private Long version;
}
