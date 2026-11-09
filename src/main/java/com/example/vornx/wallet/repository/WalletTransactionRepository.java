package com.example.vornx.wallet.repository;

import com.example.vornx.wallet.entity.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    @EntityGraph(attributePaths = {"counterpartyWallet", "counterpartyWallet.customer", "partner"})
    Page<WalletTransaction> findByWallet_Id(Long walletId, Pageable pageable);

    boolean existsByReference(String reference);
}
