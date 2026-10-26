package com.accountledger.fixeddepositservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accountledger.fixeddepositservice.dto.CreateFixedDepositRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CreateFixedDepositRequestTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void largestAllowedTenureAndRate_shouldBeValid() {
    assertTrue(validator.validate(request(3650, "100.00")).isEmpty());
  }

  @Test
  void tenureOverTenYears_shouldBeInvalid() {
    assertOnlyInvalid(request(3651, "10.00"), "tenureInDays");
    assertOnlyInvalid(request(Integer.MAX_VALUE, "10.00"), "tenureInDays");
  }

  @Test
  void interestRateOver100Percent_shouldBeInvalid() {
    assertOnlyInvalid(request(90, "100.01"), "interestRate");
    assertOnlyInvalid(request(90, "500"), "interestRate");
  }

  private void assertOnlyInvalid(CreateFixedDepositRequest request, String field) {
    Set<ConstraintViolation<CreateFixedDepositRequest>> violations = validator.validate(request);
    assertEquals(1, violations.size(), violations.toString());
    assertEquals(field, violations.iterator().next().getPropertyPath().toString());
  }

  private static CreateFixedDepositRequest request(int tenureInDays, String interestRate) {
    CreateFixedDepositRequest request = new CreateFixedDepositRequest();
    request.setClientId(1L);
    request.setPrincipalAmount(new BigDecimal("10000.00"));
    request.setInterestRate(new BigDecimal(interestRate));
    request.setPayoutAccountId(5L);
    request.setTenureInDays(tenureInDays);
    return request;
  }
}
