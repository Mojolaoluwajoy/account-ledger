package com.accountledger.clientservice.controller;

import com.accountledger.clientservice.dto.ApiResponse;
import com.accountledger.clientservice.dto.ClientResponse;
import com.accountledger.clientservice.dto.CreateClientRequest;
import com.accountledger.clientservice.dto.PageResponse;
import com.accountledger.clientservice.dto.UpdateClientRequest;
import com.accountledger.clientservice.service.ClientService;
import com.accountledger.clientservice.util.Paging;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/clients")
@Tag(name = "Clients", description = "Endpoints for managing bank clients")
@RequiredArgsConstructor
public class ClientController {

  private final ClientService clientService;

  @PostMapping
  public ResponseEntity<ApiResponse<ClientResponse>> createClient(
      @Valid @RequestBody CreateClientRequest request) {
    ClientResponse response = clientService.createClient(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("Client created successfully", response));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<ClientResponse>> getClientById(@PathVariable Long id) {
    ClientResponse response = clientService.getClientById(id);
    return ResponseEntity.ok(ApiResponse.success("Client retrieved successfully", response));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<ClientResponse>>> getClients(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "" + Paging.DEFAULT_SIZE) int size) {
    PageResponse<ClientResponse> response = clientService.getClients(page, size);
    return ResponseEntity.ok(ApiResponse.success("Clients retrieved successfully", response));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<ClientResponse>> updateClient(
      @PathVariable Long id, @Valid @RequestBody UpdateClientRequest request) {
    ClientResponse response = clientService.updateClient(id, request, request.getVersion());
    return ResponseEntity.ok(ApiResponse.success("Client updated successfully", response));
  }
}
