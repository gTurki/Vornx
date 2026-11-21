package com.example.vornx.pass;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerPassRepository extends JpaRepository<CustomerPass, Long> {

    List<CustomerPass> findByCustomerIdOrderByCreatedAtAsc(Long customerId);

    /** Always by id AND customer: nobody can see or change another customer's cards. */
    Optional<CustomerPass> findByIdAndCustomerId(Long id, Long customerId);

    long countByCustomerId(Long customerId);
}
