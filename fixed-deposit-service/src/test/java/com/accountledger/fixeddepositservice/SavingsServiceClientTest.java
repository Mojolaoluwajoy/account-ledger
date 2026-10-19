package com.accountledger.fixeddepositservice;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.accountledger.fixeddepositservice.dto.SavingsAccountLookupResponse;
import com.accountledger.fixeddepositservice.exception.InvalidPayoutAccountException;
import com.accountledger.fixeddepositservice.exception.SavingsServiceUnavailableException;
import com.accountledger.fixeddepositservice.feign.SavingsServiceApi;
import com.accountledger.fixeddepositservice.util.SavingsServiceClient;
import feign.FeignException;
import feign.Request;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsServiceClientTest {

  private static final Request REQUEST =
      Request.create(
          Request.HttpMethod.GET,
          "http://localhost:8082/api/v1/savings-accounts/5",
          Map.of(),
          null,
          StandardCharsets.UTF_8,
          null);

  @Mock private SavingsServiceApi savingsServiceApi;

  @InjectMocks private SavingsServiceClient savingsServiceClient;

  @Test
  void requirePayoutAccount_withClientsActiveAccount_shouldPass() {
    when(savingsServiceApi.getAccountById(5L)).thenReturn(account(1L, "ACTIVE"));

    assertDoesNotThrow(() -> savingsServiceClient.requirePayoutAccount(5L, 1L));
  }

  @Test
  void requirePayoutAccount_withAnotherClientsAccount_shouldThrowInvalidPayoutAccount() {
    when(savingsServiceApi.getAccountById(5L)).thenReturn(account(2L, "ACTIVE"));

    assertThrows(
        InvalidPayoutAccountException.class,
        () -> savingsServiceClient.requirePayoutAccount(5L, 1L));
  }

  @Test
  void requirePayoutAccount_withInactiveAccount_shouldThrowInvalidPayoutAccount() {
    when(savingsServiceApi.getAccountById(5L)).thenReturn(account(1L, "CLOSED"));

    assertThrows(
        InvalidPayoutAccountException.class,
        () -> savingsServiceClient.requirePayoutAccount(5L, 1L));
  }

  @Test
  void requirePayoutAccount_withUnknownAccount_shouldThrowInvalidPayoutAccount() {
    doThrow(new FeignException.NotFound("Not Found", REQUEST, null, Map.of()))
        .when(savingsServiceApi)
        .getAccountById(5L);

    assertThrows(
        InvalidPayoutAccountException.class,
        () -> savingsServiceClient.requirePayoutAccount(5L, 1L));
  }

  @Test
  void requirePayoutAccount_withServerError_shouldThrowUnavailable() {
    doThrow(new FeignException.InternalServerError("Server Error", REQUEST, null, Map.of()))
        .when(savingsServiceApi)
        .getAccountById(5L);

    assertThrows(
        SavingsServiceUnavailableException.class,
        () -> savingsServiceClient.requirePayoutAccount(5L, 1L));
  }

  private SavingsAccountLookupResponse account(Long clientId, String status) {
    SavingsAccountLookupResponse.Account account = new SavingsAccountLookupResponse.Account();
    account.setId(5L);
    account.setClientId(clientId);
    account.setStatus(status);
    SavingsAccountLookupResponse response = new SavingsAccountLookupResponse();
    response.setData(account);
    return response;
  }
}
