package com.accountledger.clientservice.dto;

import com.accountledger.clientservice.util.PhoneNumbers;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.Locale;
import lombok.Data;

@Data
public class CreateClientRequest {

  @NotBlank(message = "First name is required")
  private String firstName;

  @NotBlank(message = "Last name is required")
  private String lastName;

  @NotBlank(message = "Email is required")
  @Email(message = "Invalid email format")
  private String email;

  @NotBlank(message = "Phone number is required")
  @Pattern(
      regexp = PhoneNumbers.INTERNATIONAL_FORMAT,
      message =
          "Invalid phone number: use 0 and 10 digits for a Nigerian number,"
              + " or + and the country code")
  private String phoneNumber;

  @Past(message = "Date of birth must be in the past")
  private LocalDate dateOfBirth;

  /*
   * Emails are stored lower-cased so Jane@Gmail.com and jane@gmail.com count
   * as the same address, both in the duplicate checks and in the database's
   * unique constraint.
   */
  public void setEmail(String email) {
    this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }

  /* Stored in international form; see PhoneNumbers. */
  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = PhoneNumbers.normalize(phoneNumber);
  }
}
