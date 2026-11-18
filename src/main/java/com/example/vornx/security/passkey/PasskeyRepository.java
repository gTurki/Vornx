package com.example.vornx.security.passkey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasskeyRepository extends JpaRepository<Passkey, Long> {

    List<Passkey> findByCustomerIdOrderByCreatedAtAsc(Long customerId);

    Optional<Passkey> findByCredentialId(String credentialId);

    Optional<Passkey> findByIdAndCustomerId(Long id, Long customerId);

    Optional<Passkey> findFirstByCustomerId(Long customerId);

    List<Passkey> findByUserHandle(String userHandle);

    long countByCustomerId(Long customerId);
}
