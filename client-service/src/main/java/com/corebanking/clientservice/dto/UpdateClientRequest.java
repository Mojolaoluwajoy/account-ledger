package com.corebanking.clientservice.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateClientRequest extends CreateClientRequest {

  /*
   * The version from the client details this change is based on. When given,
   * the update is refused if the client has been changed since. Leave it out
   * to overwrite whatever is there.
   */
  private Long version;
}
