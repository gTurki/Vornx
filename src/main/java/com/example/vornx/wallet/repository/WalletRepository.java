package com.example.vornx.wallet.repository;

import com.example.vornx.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByCustomer_Id(Long customerId);

    /**
     * SELECT ... FOR UPDATE: the row stays locked until the transaction ends.
     * A second payment on the same wallet WAITS here, then sees the new balance. No double-spending.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.customer.id = :customerId")
    Optional<Wallet> lockByCustomerId(@Param("customerId") Long customerId);

    boolean existsByIban(String iban);
}
