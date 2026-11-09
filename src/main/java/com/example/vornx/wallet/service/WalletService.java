package com.example.vornx.wallet.service;
import java.util.Map;

import com.example.vornx.notification.enums.NotificationType;
import com.example.vornx.notification.service.NotificationService;

import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.util.RandomCodes;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.event.CustomerRegisteredEvent;
import com.example.vornx.customer.service.CustomerService;
import com.example.vornx.partner.entity.Partner;
import com.example.vornx.wallet.dto.WalletResponse;
import com.example.vornx.wallet.dto.WalletTransactionResponse;
import com.example.vornx.wallet.entity.Wallet;
import com.example.vornx.wallet.entity.WalletTransaction;
import com.example.vornx.wallet.enums.TransactionType;
import com.example.vornx.wallet.repository.WalletRepository;
import com.example.vornx.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * All money movement goes through here. Every change to a balance:
 *  1. locks the wallet row (no double-spending),
 *  2. changes the balance through Wallet.credit()/debit() (which enforce "never negative"),
 *  3. writes a WalletTransaction (the permanent record).
 * All in one transaction: either everything happens, or nothing does.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final CustomerService customerService;
    private final NotificationService notificationService;

    /** Until a real payment gateway is connected, top-up is simulated - and only allowed when this is true. */
    @Value("${vornx.wallet.simulated-top-up-enabled:false}")
    private boolean simulatedTopUpEnabled;

    // ---------- Wallet creation ----------

    @EventListener
    public void onCustomerRegistered(CustomerRegisteredEvent event) {
        create(customerService.getEntity(event.customerId()));
    }

    private Wallet create(Customer customer) {
        String iban;
        do {
            iban = SaudiIban.fromAccountNumber(RandomCodes.digits(18));
        } while (walletRepository.existsByIban(iban));
        Wallet wallet = walletRepository.save(new Wallet(customer, iban));
        log.info("Created wallet {} for customer {}", iban, customer.getVornxId());
        return wallet;
    }

    /** Lock the wallet (create it first if this older customer has none). */
    private Wallet lockWallet(Long customerId) {
        return walletRepository.lockByCustomerId(customerId)
                .orElseGet(() -> create(customerService.getEntity(customerId)));
    }

    // ---------- Customer: read ----------

    public WalletResponse getWallet(Long customerId) {
        Wallet wallet = walletRepository.findByCustomer_Id(customerId)
                .orElseGet(() -> create(customerService.getEntity(customerId)));
        return WalletResponse.from(wallet);
    }

    @Transactional(readOnly = true)
    public PageResponse<WalletTransactionResponse> history(Long customerId, Pageable pageable) {
        return walletRepository.findByCustomer_Id(customerId)
                .map(w -> PageResponse.from(transactionRepository.findByWallet_Id(w.getId(), pageable)
                        .map(WalletTransactionResponse::from)))
                .orElseGet(() -> new PageResponse<>(java.util.List.of(), 0, pageable.getPageSize(), 0, 0));
    }

    // ---------- Customer: actions ----------

    public WalletTransactionResponse topUp(Long customerId, BigDecimal amount) {
        if (!simulatedTopUpEnabled) {
            throw new BusinessException("TOP_UP_DISABLED", "Top-up is not available yet");
        }
        Wallet wallet = lockWallet(customerId);
        wallet.credit(amount);
        return WalletTransactionResponse.from(save(wallet, TransactionType.TOP_UP, amount, "Wallet top-up", null, null));
    }

    public WalletTransactionResponse transfer(Long customerId, String toVornxId, BigDecimal amount, String note) {
        Customer receiver = customerService.getActiveByVornxId(toVornxId);
        if (receiver.getId().equals(customerId)) {
            throw new BusinessException("SELF_TRANSFER", "You cannot transfer to yourself");
        }

        // Always lock the two wallets in the SAME order (smaller customer id first).
        // If A->B and B->A happen at the same moment, both wait in line instead of blocking each other (deadlock).
        Long first = Math.min(customerId, receiver.getId());
        Long second = Math.max(customerId, receiver.getId());
        Wallet a = lockWallet(first);
        Wallet b = lockWallet(second);
        Wallet from = first.equals(customerId) ? a : b;
        Wallet to = from == a ? b : a;

        from.debit(amount); // throws INSUFFICIENT_FUNDS -> the whole transaction is rolled back
        to.credit(amount);

        String text = StringUtils.hasText(note) ? "Transfer: " + note.trim() : "Transfer";
        WalletTransaction out = save(from, TransactionType.TRANSFER_OUT, amount, text, to, null);
        WalletTransaction in = save(to, TransactionType.TRANSFER_IN, amount, text, from, null);
        Customer sender = from.getCustomer();
        notificationService.notifyCustomer(receiver.getId(), NotificationType.TRANSFER_RECEIVED,
                Map.of("from", sender.getFirstName() + " " + sender.getLastName().charAt(0) + ".", "amount", amount),
                NotificationService.Ref.of("WALLET_TRANSACTION", in.getId()));
        return WalletTransactionResponse.from(out);
    }

    // ---------- For other modules (subscriptions...) ----------

    /** Take money from the customer to pay a partner. */
    public WalletTransaction charge(Long customerId, BigDecimal amount, Partner partner, String description) {
        Wallet wallet = lockWallet(customerId);
        wallet.debit(amount);
        return save(wallet, TransactionType.PAYMENT, amount, description, null, partner);
    }

    /** Give money back (rejected or cancelled request). */
    public WalletTransaction refund(Long customerId, BigDecimal amount, Partner partner, String description) {
        Wallet wallet = lockWallet(customerId);
        wallet.credit(amount);
        return save(wallet, TransactionType.REFUND, amount, description, null, partner);
    }

    private WalletTransaction save(Wallet wallet, TransactionType type, BigDecimal amount, String description,
                                   Wallet counterparty, Partner partner) {
        String reference;
        do {
            reference = "TX-" + RandomCodes.alphanumeric(10);
        } while (transactionRepository.existsByReference(reference));
        return transactionRepository.save(
                new WalletTransaction(reference, wallet, type, amount, description, counterparty, partner));
    }
}
