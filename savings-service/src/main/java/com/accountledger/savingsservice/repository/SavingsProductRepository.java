package com.accountledger.savingsservice.repository;

import com.accountledger.savingsservice.entity.SavingsProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SavingsProductRepository extends JpaRepository<SavingsProduct, Long> {

  boolean existsByShortName(String shortName);
}
