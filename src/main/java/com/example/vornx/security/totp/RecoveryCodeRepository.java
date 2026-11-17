package com.example.vornx.security.totp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {

    Optional<RecoveryCode> findByCustomerIdAndCodeHashAndUsedAtIsNull(Long customerId, String codeHash);

    long countByCustomerIdAndUsedAtIsNull(Long customerId);

    @Modifying
    @Query("DELETE FROM RecoveryCode r WHERE r.customerId = :customerId")
    int deleteAllForCustomer(@Param("customerId") Long customerId);
}
