package com.example.vornx.customer.repository;

import com.example.vornx.common.enums.AccountStatus;
import com.example.vornx.customer.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByEmail(String email);

    Optional<Customer> findByVornxId(String vornxId);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByVornxId(String vornxId);

    long countByStatus(AccountStatus status);

    /** Admin "العملاء": search by name, email, mobile or VORNX ID (null q = everyone), optionally by status. */
    @Query("""
            select c from Customer c
            where (:status is null or c.status = :status)
              and (:q is null
                   or lower(c.email) like lower(concat('%', :q, '%'))
                   or c.phone like concat('%', :q, '%')
                   or upper(c.vornxId) like upper(concat('%', :q, '%'))
                   or lower(concat(c.firstName, ' ', c.lastName)) like lower(concat('%', :q, '%')))
            """)
    Page<Customer> search(@Param("q") String q, @Param("status") AccountStatus status, Pageable pageable);

    /** Checked on every customer request while mobile verification is on (primary key lookup). */
    boolean existsByIdAndPhoneVerifiedAtIsNotNull(Long id);
}
