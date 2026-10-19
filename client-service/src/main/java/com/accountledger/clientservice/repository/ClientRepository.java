package com.accountledger.clientservice.repository;

import com.accountledger.clientservice.entity.Client;
import com.accountledger.clientservice.entity.ClientStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

  boolean existsByEmail(String email);

  boolean existsByPhoneNumber(String phoneNumber);

  Optional<Client> findByEmail(String email);

  List<Client> findByStatusAndFineractClientIdIsNull(ClientStatus status);

  List<Client> findByFineractSyncPendingTrueAndFineractClientIdIsNotNull();
}
