package com.corebanking.clientservice.util;

import com.corebanking.clientservice.dto.ClientResponse;
import com.corebanking.clientservice.dto.CreateClientRequest;
import com.corebanking.clientservice.entity.Client;
import com.corebanking.clientservice.entity.ClientStatus;

public class ClientMapper {

  public static Client toEntity(CreateClientRequest request) {
    Client client = new Client();
    client.setFirstName(request.getFirstName());
    client.setLastName(request.getLastName());
    client.setEmail(request.getEmail());
    client.setPhoneNumber(request.getPhoneNumber());
    client.setDateOfBirth(request.getDateOfBirth());
    client.setStatus(ClientStatus.PENDING);
    return client;
  }

  public static ClientResponse toResponse(Client client) {
    return ClientResponse.builder()
        .id(client.getId())
        .firstName(client.getFirstName())
        .lastName(client.getLastName())
        .email(client.getEmail())
        .phoneNumber(client.getPhoneNumber())
        .dateOfBirth(client.getDateOfBirth())
        .status(client.getStatus())
        .createdAt(client.getCreatedAt())
        .fineractClientId(client.getFineractClientId())
        .fineractSyncPending(client.isFineractSyncPending())
        .fineractSyncError(client.getFineractSyncError())
        .build();
  }

  private ClientMapper() {}
}
