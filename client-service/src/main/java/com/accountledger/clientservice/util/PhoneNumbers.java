package com.accountledger.clientservice.util;

/*
 * Phone numbers are stored in international form (+2348142988584), so
 * 08142988584, 2348142988584 and +234 814 298 8584 count as the same number,
 * both in the duplicate checks and in the database's unique constraint.
 *
 * A number without a country code is taken to be Nigerian (0 and 10 digits).
 * Numbers from elsewhere must start with + and their country code.
 */
public final class PhoneNumbers {

  /* What a stored number looks like: + and 8 to 15 digits, no leading zero. */
  public static final String INTERNATIONAL_FORMAT = "^\\+[1-9]\\d{7,14}$";

  private PhoneNumbers() {}

  /*
   * Returns the number in international form, or the input with only spaces,
   * dashes, dots and brackets removed when it cannot be read as a phone
   * number (validation then refuses it).
   */
  public static String normalize(String phoneNumber) {
    if (phoneNumber == null) {
      return null;
    }
    String compact = phoneNumber.replaceAll("[\\s\\-.()]", "");
    if (compact.startsWith("00")) {
      compact = "+" + compact.substring(2);
    }
    if (compact.matches("0\\d{10}")) {
      return "+234" + compact.substring(1);
    }
    if (compact.matches("234\\d{10}")) {
      return "+" + compact;
    }
    if (compact.matches("\\+2340\\d{10}")) {
      return "+234" + compact.substring(5);
    }
    return compact;
  }
}
