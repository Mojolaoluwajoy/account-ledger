package com.accountledger.savingsservice.service;

import com.accountledger.savingsservice.dto.CreateSavingsProductRequest;
import com.accountledger.savingsservice.dto.SavingsProductResponse;
import com.accountledger.savingsservice.entity.SavingsProduct;
import com.accountledger.savingsservice.exception.DuplicateProductException;
import com.accountledger.savingsservice.exception.ProductNotFoundException;
import com.accountledger.savingsservice.repository.SavingsProductRepository;
import com.accountledger.savingsservice.util.SavingsProductMapper;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavingsProductService {

  private final SavingsProductRepository savingsProductRepository;

  public SavingsProductResponse createProduct(CreateSavingsProductRequest request) {
    log.info("Creating savings product with short name: {}", request.getShortName());

    if (savingsProductRepository.existsByShortName(request.getShortName())) {
      throw new DuplicateProductException(
          "Savings product with short name " + request.getShortName() + " already exists");
    }

    SavingsProduct savedProduct =
        savingsProductRepository.save(SavingsProductMapper.toEntity(request));

    log.info("Savings product created successfully with ID: {}", savedProduct.getId());
    return SavingsProductMapper.toResponse(savedProduct);
  }

  public SavingsProductResponse getProductById(Long id) {
    log.info("Fetching savings product with ID: {}", id);

    SavingsProduct product =
        savingsProductRepository
            .findById(id)
            .orElseThrow(
                () -> new ProductNotFoundException("Savings product not found with ID: " + id));

    return SavingsProductMapper.toResponse(product);
  }

  public List<SavingsProductResponse> getAllProducts() {
    log.info("Fetching all savings products");

    return savingsProductRepository.findAll().stream()
        .map(SavingsProductMapper::toResponse)
        .collect(Collectors.toList());
  }
}
