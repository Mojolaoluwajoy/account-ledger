package com.corebanking.clientservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FineractCreateClientRequest {
  private Integer officeId;
  private Integer legalFormId;
  private String firstname;
  private String lastname;
  private boolean active;
  private String activationDate;
  private String dateFormat;
  private String locale;
  private String mobileNo;
  private String emailAddress;
  private String externalId;
}
