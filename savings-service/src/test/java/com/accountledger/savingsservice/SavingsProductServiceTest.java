package com.accountledger.savingsservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.accountledger.savingsservice.dto.CreateSavingsProductRequest;
import com.accountledger.savingsservice.dto.SavingsProductResponse;
import com.accountledger.savingsservice.entity.SavingsProduct;
import com.accountledger.savingsservice.exception.DuplicateProductException;
import com.accountledger.savingsservice.exception.ProductNotFoundException;
import com.accountledger.savingsservice.repository.SavingsProductRepository;
import com.accountledger.savingsservice.service.SavingsProductService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class SavingsProductServiceTest {

  @Mock private SavingsProductRepository savingsProductRepository;

  @InjectMocks private SavingsProductService savingsProductService;

  private CreateSavingsProductRequest validRequest;
  private SavingsProduct savedProduct;

  @BeforeEach
  void setUp() {
    validRequest = new CreateSavingsProductRequest();
    validRequest.setName("Regular Savings");
    validRequest.setShortName("REGSAV");
    validRequest.setDescription("Basic savings account");
    validRequest.setNominalAnnualInterestRate(new BigDecimal("5.00"));
    validRequest.setMinimumOpeningBalance(new BigDecimal("100.00"));
    validRequest.setMinimumBalance(new BigDecimal("50.00"));
    validRequest.setEnforceMinimumBalance(true);

    savedProduct = new SavingsProduct();
    savedProduct.setId(1L);
    savedProduct.setName("Regular Savings");
    savedProduct.setShortName("REGSAV");
    savedProduct.setDescription("Basic savings account");
    savedProduct.setNominalAnnualInterestRate(new BigDecimal("5.00"));
    savedProduct.setMinimumOpeningBalance(new BigDecimal("100.00"));
    savedProduct.setMinimumBalance(new BigDecimal("50.00"));
    savedProduct.setEnforceMinimumBalance(true);
  }

  @Test
  void createProduct_withValidRequest_shouldReturnProductResponse() {
    when(savingsProductRepository.existsByShortName(validRequest.getShortName())).thenReturn(false);
    when(savingsProductRepository.save(any(SavingsProduct.class))).thenReturn(savedProduct);

    SavingsProductResponse response = savingsProductService.createProduct(validRequest);

    assertNotNull(response);
    assertEquals("Regular Savings", response.getName());
    assertEquals("REGSAV", response.getShortName());
    verify(savingsProductRepository, times(1)).save(any(SavingsProduct.class));
  }

  @Test
  void createProduct_withDuplicateShortName_shouldThrowException() {
    when(savingsProductRepository.existsByShortName(validRequest.getShortName())).thenReturn(true);

    assertThrows(
        DuplicateProductException.class,
        () -> {
          savingsProductService.createProduct(validRequest);
        });

    verify(savingsProductRepository, never()).save(any(SavingsProduct.class));
  }

  @Test
  void createProduct_whenShortNameTakenConcurrently_shouldThrowDuplicateProductException() {
    when(savingsProductRepository.existsByShortName(validRequest.getShortName())).thenReturn(false);
    when(savingsProductRepository.save(any(SavingsProduct.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate key"));

    assertThrows(
        DuplicateProductException.class, () -> savingsProductService.createProduct(validRequest));
  }

  @Test
  void getProductById_withValidId_shouldReturnProductResponse() {
    when(savingsProductRepository.findById(1L)).thenReturn(Optional.of(savedProduct));

    SavingsProductResponse response = savingsProductService.getProductById(1L);

    assertNotNull(response);
    assertEquals(1L, response.getId());
    assertEquals("Regular Savings", response.getName());
  }

  @Test
  void getProductById_withInvalidId_shouldThrowException() {
    when(savingsProductRepository.findById(99L)).thenReturn(Optional.empty());

    assertThrows(
        ProductNotFoundException.class,
        () -> {
          savingsProductService.getProductById(99L);
        });
  }
}
