package com.example.vornx.payment.service;

import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.service.CustomerService;
import com.example.vornx.email.FrontendProperties;
import com.example.vornx.notification.service.NotificationService;
import com.example.vornx.payment.entity.TopUp;
import com.example.vornx.payment.enums.TopUpStatus;
import com.example.vornx.payment.gateway.MoyasarClient;
import com.example.vornx.payment.repository.TopUpRepository;
import com.example.vornx.wallet.entity.WalletTransaction;
import com.example.vornx.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The money rules of card top-up: credit ONLY a verified, exact payment, and only ONCE.
 * Moyasar and the database are fakes (Mockito), so this runs in milliseconds.
 */
class TopUpRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

    private TopUpRepository repository;
    private WalletService wallet;
    private MoyasarClient moyasar;
    private TopUp topUp;

    private TopUpService service(Instant now) {
        return new TopUpService(repository, mock(CustomerService.class), wallet, mock(NotificationService.class), moyasar,
                new FrontendProperties("a", "p", "https://my.vornx.sa"), mock(PlatformTransactionManager.class),
                Clock.fixed(now, ZoneOffset.UTC), false, "");
    }

    @BeforeEach
    void setUp() {
        repository = mock(TopUpRepository.class);
        wallet = mock(WalletService.class);
        moyasar = mock(MoyasarClient.class);
        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(7L);

        topUp = new TopUp("TU-TEST", customer, new BigDecimal("150.50"), "SAR", TopUp.PROVIDER_MOYASAR, NOW.plusSeconds(1800));
        topUp.attachInvoice("inv-1", "https://checkout.moyasar.com/invoices/inv-1");
        when(repository.findById(1L)).thenReturn(Optional.of(topUp));
        when(repository.lockById(1L)).thenReturn(Optional.of(topUp));

        WalletTransaction credit = mock(WalletTransaction.class);
        when(credit.getId()).thenReturn(99L);
        when(wallet.creditTopUp(anyLong(), any(), anyString())).thenReturn(credit);
    }

    private void moyasarSays(String status, long amount, String currency) {
        when(moyasar.fetchInvoice("inv-1")).thenReturn(new MoyasarClient.Invoice("inv-1", status, amount, currency, null, null));
    }

    @Test
    void paidWithTheExactAmount_creditsTheWallet() {
        moyasarSays("paid", 15050, "SAR");
        service(NOW).sync(1L);
        assertEquals(TopUpStatus.PAID, topUp.getStatus());
        verify(wallet).creditTopUp(7L, new BigDecimal("150.50"), "Card top-up TU-TEST");
    }

    @Test
    void checkingTwice_creditsOnlyOnce() {
        moyasarSays("paid", 15050, "SAR");
        TopUpService s = service(NOW);
        s.sync(1L); // return page
        s.sync(1L); // Moyasar callback, a moment later
        verify(wallet, times(1)).creditTopUp(anyLong(), any(), anyString());
    }

    @Test
    void paidButWrongAmount_isNotCredited() {
        moyasarSays("paid", 100, "SAR"); // 1.00 SAR instead of 150.50
        service(NOW).sync(1L);
        assertEquals(TopUpStatus.FAILED, topUp.getStatus());
        verify(wallet, never()).creditTopUp(anyLong(), any(), anyString());
    }

    @Test
    void paidInAnotherCurrency_isNotCredited() {
        moyasarSays("paid", 15050, "USD");
        service(NOW).sync(1L);
        assertEquals(TopUpStatus.FAILED, topUp.getStatus());
        verify(wallet, never()).creditTopUp(anyLong(), any(), anyString());
    }

    @Test
    void stillPaying_staysPending() {
        moyasarSays("initiated", 15050, "SAR");
        service(NOW.plusSeconds(600)).sync(1L);
        assertEquals(TopUpStatus.PENDING, topUp.getStatus());
    }

    @Test
    void neverPaid_expiresAfterTheDeadline() {
        moyasarSays("initiated", 15050, "SAR");
        service(NOW.plusSeconds(1800 + 11 * 60)).sync(1L);
        assertEquals(TopUpStatus.EXPIRED, topUp.getStatus());
    }

    @Test
    void cardDeclined_failsWithoutCredit() {
        moyasarSays("failed", 15050, "SAR");
        service(NOW).sync(1L);
        assertEquals(TopUpStatus.FAILED, topUp.getStatus());
        verify(wallet, never()).creditTopUp(anyLong(), any(), anyString());
    }

    @Test
    void moyasarUnreachable_changesNothing() {
        when(moyasar.fetchInvoice("inv-1")).thenThrow(new ResourceAccessException("timeout"));
        service(NOW).sync(1L);
        assertEquals(TopUpStatus.PENDING, topUp.getStatus());
    }

    @Test
    void callbackForAnUnknownInvoice_isIgnored() {
        when(repository.findByProviderInvoiceId("forged")).thenReturn(Optional.empty());
        service(NOW).onProviderCallback("forged");
        verify(moyasar, never()).fetchInvoice(anyString());
    }

    @Test
    void halalas() {
        assertEquals(15050, TopUpService.toHalalas(new BigDecimal("150.50")));
        assertEquals(1000, TopUpService.toHalalas(new BigDecimal("10")));
    }
}
