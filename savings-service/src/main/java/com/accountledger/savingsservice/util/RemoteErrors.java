package com.accountledger.savingsservice.util;

import feign.FeignException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Turns a failed call to another of our services into a message a person can
 * act on. Feign's own message starts with the status and URL, so the other
 * service's explanation (at the end) was cut off when stored.
 */
public final class RemoteErrors {

  // Our services answer errors as {"success":false,"message":"..."}.
  private static final Pattern MESSAGE =
      Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

  /* For example "Savings service answered 409: Account SAV123 is not active". */
  public static String describe(String service, Exception e) {
    if (e instanceof FeignException feignException) {
      if (feignException.status() <= 0) {
        return service + " could not be reached: " + e.getMessage();
      }
      return service + " answered " + feignException.status() + ": " + reason(feignException);
    }
    return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
  }

  /*
   * True when the other service refused the request itself (a 4xx answer), so
   * sending it again would be refused again. 408 and 429 are the exceptions:
   * they mean "not now", and a later attempt can succeed.
   */
  public static boolean isPermanent(Exception e) {
    return e instanceof FeignException.FeignClientException clientError
        && clientError.status() != 408
        && clientError.status() != 429;
  }

  private static String reason(FeignException e) {
    String body = e.contentUTF8();
    if (body == null || body.isBlank()) {
      return "no explanation given";
    }
    Matcher message = MESSAGE.matcher(body);
    if (message.find()) {
      return message.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }
    return body.strip();
  }

  private RemoteErrors() {}
}
