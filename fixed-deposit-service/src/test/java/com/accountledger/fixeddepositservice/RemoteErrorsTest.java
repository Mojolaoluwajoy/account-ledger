package com.accountledger.fixeddepositservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accountledger.fixeddepositservice.util.RemoteErrors;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RemoteErrorsTest {

  private static final Request REQUEST =
      Request.create(
          Request.HttpMethod.POST,
          "http://localhost:8084/api/v1/ledger/entries",
          Map.of(),
          null,
          StandardCharsets.UTF_8,
          null);

  @Test
  void describe_withErrorBodyFromOurService_shouldUseItsMessage() {
    byte[] body =
        "{\"message\":\"Account \\\"SAV1\\\" is not active\",\"success\":false}"
            .getBytes(StandardCharsets.UTF_8);

    String described =
        RemoteErrors.describe(
            "Savings service", new FeignException.Conflict("Conflict", REQUEST, body, Map.of()));

    assertEquals("Savings service answered 409: Account \"SAV1\" is not active", described);
  }

  @Test
  void describe_withEmptyBody_shouldSayNoExplanationWasGiven() {
    String described =
        RemoteErrors.describe(
            "Ledger service",
            new FeignException.BadGateway("Bad Gateway", REQUEST, null, Map.of()));

    assertEquals("Ledger service answered 502: no explanation given", described);
  }

  @Test
  void describe_whenServiceUnreachable_shouldSaySo() {
    String described =
        RemoteErrors.describe(
            "Ledger service",
            new RetryableException(
                -1, "Connection refused", Request.HttpMethod.POST, (Long) null, REQUEST));

    assertEquals("Ledger service could not be reached: Connection refused", described);
  }

  @Test
  void describe_withOtherException_shouldKeepItsMessage() {
    assertEquals(
        "ledger down",
        RemoteErrors.describe("Ledger service", new RuntimeException("ledger down")));
  }

  @Test
  void isPermanent_shouldBeTrueOnlyForClientErrorsThatWouldRepeat() {
    assertTrue(
        RemoteErrors.isPermanent(
            new FeignException.BadRequest("Bad Request", REQUEST, null, Map.of())));
    assertTrue(
        RemoteErrors.isPermanent(new FeignException.Conflict("Conflict", REQUEST, null, Map.of())));

    assertFalse(
        RemoteErrors.isPermanent(
            new FeignException.FeignClientException(
                408, "Request Timeout", REQUEST, null, Map.of())));
    assertFalse(
        RemoteErrors.isPermanent(
            new FeignException.TooManyRequests("Too Many Requests", REQUEST, null, Map.of())));
    assertFalse(
        RemoteErrors.isPermanent(
            new FeignException.BadGateway("Bad Gateway", REQUEST, null, Map.of())));
    assertFalse(RemoteErrors.isPermanent(new RuntimeException("ledger down")));
  }
}
