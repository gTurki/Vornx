package com.example.vornx.customer.repository;

import com.example.vornx.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByEmail(String email);

    Optional<Customer> findByVornxId(String vornxId);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByVornxId(String vornxId);

    /** Checked on every customer request while mobile verification is on (primary key lookup). */
    boolean existsByIdAndPhoneVerifiedAtIsNotNull(Long id);
}
