package com.example.vornx.payment.service;

import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.common.util.RandomCodes;
import com.example.vornx.customer.service.CustomerService;
import com.example.vornx.email.FrontendProperties;
import com.example.vornx.notification.enums.NotificationType;
import com.example.vornx.notification.service.NotificationService;
import com.example.vornx.payment.dto.TopUpDtos.TopUpOptions;
import com.example.vornx.payment.dto.TopUpDtos.TopUpResponse;
import com.example.vornx.payment.entity.TopUp;
import com.example.vornx.payment.enums.TopUpStatus;
import com.example.vornx.payment.gateway.MoyasarClient;
import com.example.vornx.payment.repository.TopUpRepository;
import com.example.vornx.wallet.entity.WalletTransaction;
import com.example.vornx.wallet.service.WalletService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Wallet top-up by card, through Moyasar's hosted invoice page.
 *
 *   1. start():  save a PENDING top-up, create a Moyasar invoice, give the app its payment page URL.
 *   2. The customer pays on Moyasar's page (we never see the card), then comes back to /wallet?topup=REF.
 *   3. sync():   ask Moyasar "is invoice X paid, for how much?" with our secret key. Only then credit the wallet.
 *
 * sync() is triggered from 3 places - the return page, Moyasar's callback, and a job every minute -
 * and may run for the same top-up at the same time. The row lock + "only if PENDING" make it credit ONCE.
 *
 * External HTTP calls are made OUTSIDE database transactions, so a slow Moyasar never holds DB locks.
 */
@Slf4j
@Service
public class TopUpService {

    static final String CURRENCY = "SAR";
    static final BigDecimal MIN = new BigDecimal("10.00");
    static final BigDecimal MAX = new BigDecimal("10000.00");
    private static final Duration PAY_WITHIN = Duration.ofMinutes(30);
    private static final int MAX_PENDING_PER_HOUR = 5;

    private final TopUpRepository repository;
    private final CustomerService customerService;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final MoyasarClient moyasar;
    private final FrontendProperties frontend;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final boolean simulatedEnabled;
    private final String callbackUrl;

    public TopUpService(TopUpRepository repository, CustomerService customerService, WalletService walletService,
                        NotificationService notificationService, MoyasarClient moyasar, FrontendProperties frontend,
                        PlatformTransactionManager txManager, Clock clock,
                        @Value("${vornx.wallet.simulated-top-up-enabled:false}") boolean simulatedEnabled,
                        @Value("${vornx.payments.moyasar.callback-url:}") String callbackUrl) {
        this.repository = repository;
        this.customerService = customerService;
        this.walletService = walletService;
        this.notificationService = notificationService;
        this.moyasar = moyasar;
        this.frontend = frontend;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
        this.simulatedEnabled = simulatedEnabled;
        this.callbackUrl = callbackUrl;
    }

    public TopUpOptions options() {
        return new TopUpOptions(moyasar.isConfigured(), moyasar.isTestMode(), simulatedEnabled, MIN, MAX);
    }

    // ---------- 1. start ----------

    public TopUpResponse start(Long customerId, BigDecimal amount) {
        if (!moyasar.isConfigured()) {
            throw new BusinessException("TOP_UP_DISABLED", "Card top-up is not available yet");
        }
        BigDecimal value = amount.setScale(2, RoundingMode.UNNECESSARY);
        Instant now = clock.instant();

        TopUp saved = tx.execute(s -> {
            // A small brake against someone opening hundreds of payment pages.
            long open = repository.countByCustomer_IdAndStatusAndCreatedAtAfter(
                    customerId, TopUpStatus.PENDING, now.minus(Duration.ofHours(1)));
            if (open >= MAX_PENDING_PER_HOUR) {
                throw new BusinessException("TOO_MANY_PENDING", "Too many unfinished top-ups. Try again later.");
            }
            String reference;
            do {
                reference = "TU-" + RandomCodes.alphanumeric(10);
            } while (repository.existsByReference(reference));
            return repository.save(new TopUp(reference, customerService.getEntity(customerId), value, CURRENCY,
                    TopUp.PROVIDER_MOYASAR, now.plus(PAY_WITHIN)));
        });

        String back = frontend.customerUrl() + "/wallet?topup=" + saved.getReference();
        MoyasarClient.Invoice invoice;
        try {
            invoice = moyasar.createInvoice(toHalalas(value), CURRENCY, "VORNX wallet top-up " + saved.getReference(),
                    saved.getExpiresAt(), back, back, callbackUrl);
        } catch (RestClientException e) {
            log.error("Moyasar: creating invoice for top-up {} failed: {}", saved.getReference(), e.getMessage());
            tx.executeWithoutResult(s -> repository.lockById(saved.getId()).ifPresent(t -> t.markFailed("Could not create invoice")));
            throw new BusinessException("PAYMENT_PROVIDER_ERROR", "The payment service is not responding. Try again.");
        }

        return tx.execute(s -> {
            TopUp t = repository.lockById(saved.getId()).orElseThrow();
            t.attachInvoice(invoice.id(), invoice.url());
            return TopUpResponse.from(t);
        });
    }

    // ---------- 2. read (from the return page) ----------

    /** The app polls this after coming back from Moyasar. Checks with Moyasar if still pending. */
    public TopUpResponse getForCustomer(Long customerId, String reference) {
        TopUp t = repository.findByReferenceAndCustomer_Id(reference, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("TopUp", reference));
        // Don't ask Moyasar more than once every 1.5 seconds for the same top-up (the app polls every 2 s).
        boolean recent = t.getLastCheckedAt() != null && t.getLastCheckedAt().isAfter(clock.instant().minusMillis(1500));
        if (t.isPending() && !recent) {
            sync(t.getId());
            t = repository.findById(t.getId()).orElseThrow();
        }
        return TopUpResponse.from(t);
    }

    // ---------- 3. Moyasar's callback ----------

    /**
     * Moyasar POSTs the invoice when it is paid. Anyone could POST to this URL, so the body is only a hint:
     * we take the invoice id, and ask Moyasar ourselves.
     */
    public void onProviderCallback(String invoiceId) {
        if (invoiceId == null || invoiceId.isBlank() || invoiceId.length() > 64) {
            return;
        }
        repository.findByProviderInvoiceId(invoiceId).filter(TopUp::isPending).ifPresent(t -> sync(t.getId()));
    }

    // ---------- 4. safety net ----------

    /** Customers who paid but closed the browser before returning, while the callback also failed. */
    @Scheduled(fixedDelayString = "${vornx.payments.reconcile-every:60s}", initialDelayString = "30s")
    public void reconcile() {
        List<Long> ids = repository.findIdsByStatusCreatedBefore(TopUpStatus.PENDING,
                clock.instant().minus(Duration.ofMinutes(1)), PageRequest.of(0, 50));
        for (Long id : ids) {
            try {
                sync(id);
            } catch (RuntimeException e) {
                log.warn("Top-up reconcile failed for id {}: {}", id, e.getMessage());
            }
        }
    }

    // ---------- the one place that decides ----------

    void sync(Long topUpId) {
        TopUp snapshot = repository.findById(topUpId).orElseThrow();
        if (!snapshot.isPending()) {
            return;
        }
        Instant now = clock.instant();

        MoyasarClient.Invoice invoice = null;
        if (snapshot.getProviderInvoiceId() != null) {
            try {
                invoice = moyasar.fetchInvoice(snapshot.getProviderInvoiceId());
            } catch (RestClientException e) {
                log.warn("Moyasar: fetching invoice for top-up {} failed: {}", snapshot.getReference(), e.getMessage());
                return; // try again later; nothing changes
            }
        }
        MoyasarClient.Invoice result = invoice;

        tx.executeWithoutResult(s -> {
            TopUp t = repository.lockById(topUpId).orElseThrow();
            if (!t.isPending()) {
                return; // someone else finished it while we were asking Moyasar
            }
            t.checked(now);
            if (result != null && result.isPaid()) {
                if (result.amount() != toHalalas(t.getAmount()) || !CURRENCY.equalsIgnoreCase(result.currency())) {
                    // Should never happen. Don't credit a wrong amount; a human must look at it.
                    log.error("Top-up {} PAID with a different amount: expected {} {} halalas, Moyasar says {} {}",
                            t.getReference(), toHalalas(t.getAmount()), CURRENCY, result.amount(), result.currency());
                    t.markFailed("Paid amount does not match (" + result.amount() + " " + result.currency() + ")");
                    return;
                }
                WalletTransaction credit = walletService.creditTopUp(
                        t.getCustomer().getId(), t.getAmount(), "Card top-up " + t.getReference());
                t.markPaid(credit, now);
                notificationService.notifyCustomer(t.getCustomer().getId(), NotificationType.WALLET_TOPPED_UP,
                        Map.of("amount", t.getAmount()), NotificationService.Ref.of("WALLET_TRANSACTION", credit.getId()));
                log.info("Top-up {} paid: {} {} credited", t.getReference(), t.getAmount(), CURRENCY);
            } else if (result != null && result.isDead()) {
                if ("expired".equals(result.status())) {
                    t.markExpired();
                } else {
                    t.markFailed("Moyasar status: " + result.status());
                }
            } else if (now.isAfter(t.getExpiresAt().plus(Duration.ofMinutes(10)))) {
                // Moyasar refuses payment after expired_at; 10 minutes of margin for a payment in progress.
                t.markExpired();
            }
        });
    }

    static long toHalalas(BigDecimal sar) {
        return sar.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }
}
