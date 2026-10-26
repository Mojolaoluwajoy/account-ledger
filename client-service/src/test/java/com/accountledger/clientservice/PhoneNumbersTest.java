package com.accountledger.clientservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accountledger.clientservice.dto.CreateClientRequest;
import com.accountledger.clientservice.util.PhoneNumbers;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void normalize_shouldWriteEveryFormOfANigerianNumberTheSameWay() {
    for (String written :
        new String[] {
          "08142988584",
          "0814 298 8584",
          "0814-298-8584",
          "2348142988584",
          "+2348142988584",
          "+234 814 298 8584",
          "+234 (0) 814 298 8584",
          "002348142988584"
        }) {
      assertEquals("+2348142988584", PhoneNumbers.normalize(written), written);
    }
  }

  @Test
  void normalize_shouldKeepInternationalNumbersFromElsewhere() {
    assertEquals("+447911123456", PhoneNumbers.normalize("+44 7911 123456"));
    assertEquals("+447911123456", PhoneNumbers.normalize("00447911123456"));
  }

  @Test
  void request_shouldStoreThePhoneNumberInInternationalForm() {
    CreateClientRequest request = request("0814 298 8584");

    assertEquals("+2348142988584", request.getPhoneNumber());
    assertTrue(validator.validate(request).isEmpty());
  }

  @Test
  void request_withNumberThatIsNotAPhoneNumber_shouldBeInvalid() {
    for (String written : new String[] {"12345", "0814298858", "phone", "+0123456789"}) {
      Set<ConstraintViolation<CreateClientRequest>> violations =
          validator.validate(request(written));

      assertEquals(1, violations.size(), written);
      assertEquals("phoneNumber", violations.iterator().next().getPropertyPath().toString());
    }
  }

  private static CreateClientRequest request(String phoneNumber) {
    CreateClientRequest request = new CreateClientRequest();
    request.setFirstName("Jane");
    request.setLastName("Doe");
    request.setEmail("jane@example.com");
    request.setPhoneNumber(phoneNumber);
    request.setDateOfBirth(LocalDate.of(1990, 1, 1));
    return request;
  }
}
