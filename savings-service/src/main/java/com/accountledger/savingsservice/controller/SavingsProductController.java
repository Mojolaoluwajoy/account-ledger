package com.accountledger.savingsservice.controller;

import com.accountledger.savingsservice.dto.ApiResponse;
import com.accountledger.savingsservice.dto.CreateSavingsProductRequest;
import com.accountledger.savingsservice.dto.SavingsProductResponse;
import com.accountledger.savingsservice.service.SavingsProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/savings-products")
@RequiredArgsConstructor
@Tag(name = "Savings Products", description = "Endpoints for managing savings product templates")
public class SavingsProductController {

  private final SavingsProductService savingsProductService;

  @Operation(
      summary = "Create a new savings product",
      description =
          "Creates a new savings product template that clients can open accounts under. "
              + "Equivalent to Admin → Products → Savings Products on Mifos.")
  @PostMapping
  public ResponseEntity<ApiResponse<SavingsProductResponse>> createProduct(
      @Valid @RequestBody CreateSavingsProductRequest request) {
    log.info("Request received to create savings product");
    SavingsProductResponse response = savingsProductService.createProduct(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("Savings product created successfully", response));
  }

  @Operation(
      summary = "Get savings product by ID",
      description =
          "Fetches a single savings product by its system ID. "
              + "Used when opening a savings account to apply product rules.")
  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<SavingsProductResponse>> getProductById(@PathVariable Long id) {
    log.info("Request received to fetch savings product with ID: {}", id);
    SavingsProductResponse response = savingsProductService.getProductById(id);
    return ResponseEntity.ok(
        ApiResponse.success("Savings product retrieved successfully", response));
  }

  @Operation(
      summary = "Get all savings products",
      description =
          "Returns all configured savings products. "
              + "Used when a client wants to choose which product to open an account under.")
  @GetMapping
  public ResponseEntity<ApiResponse<List<SavingsProductResponse>>> getAllProducts() {
    log.info("Request received to fetch all savings products");
    List<SavingsProductResponse> response = savingsProductService.getAllProducts();
    return ResponseEntity.ok(
        ApiResponse.success("Savings products retrieved successfully", response));
  }
}
