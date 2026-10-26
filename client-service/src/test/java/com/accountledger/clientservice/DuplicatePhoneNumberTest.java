package com.accountledger.clientservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.accountledger.clientservice.dto.CreateClientRequest;
import com.accountledger.clientservice.exception.DuplicatePhoneNumberException;
import com.accountledger.clientservice.feign.FineractClient;
import com.accountledger.clientservice.repository.ClientRepository;
import com.accountledger.clientservice.service.ClientService;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class DuplicatePhoneNumberTest {

  @Autowired private ClientService clientService;
  @Autowired private ClientRepository clientRepository;

  @MockitoBean private FineractClient fineractClient;

  @AfterEach
  void tearDown() {
    clientRepository.deleteAll();
  }

  @Test
  void sameNumberWrittenDifferently_shouldBeRefusedAsADuplicate() {
    Long id = clientService.createClient(request("ada@example.com", "08142988584")).getId();

    assertThrows(
        DuplicatePhoneNumberException.class,
        () -> clientService.createClient(request("bisi@example.com", "+234 814 298 8584")));

    assertEquals(1, clientRepository.count());
    assertEquals("+2348142988584", clientRepository.findById(id).get().getPhoneNumber());
  }

  @Test
  void updateWithOwnNumberWrittenDifferently_shouldNotBeADuplicate() {
    Long id = clientService.createClient(request("ada@example.com", "08142988584")).getId();

    clientService.updateClient(id, request("ada@example.com", "2348142988584"), null);

    assertEquals("+2348142988584", clientRepository.findById(id).get().getPhoneNumber());
  }

  private static CreateClientRequest request(String email, String phoneNumber) {
    CreateClientRequest request = new CreateClientRequest();
    request.setFirstName("Ada");
    request.setLastName("Obi");
    request.setEmail(email);
    request.setPhoneNumber(phoneNumber);
    request.setDateOfBirth(LocalDate.of(1990, 4, 2));
    return request;
  }
}
