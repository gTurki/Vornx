package com.example.vornx.wallet;

import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.subscription.enums.BillingPeriod;
import com.example.vornx.wallet.entity.Wallet;
import com.example.vornx.wallet.service.SaudiIban;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletRulesTest {

    private static final ZoneId RIYADH = ZoneId.of("Asia/Riyadh");

    @Test
    void generatedIbanIsValidAndHas24Characters() {
        String iban = SaudiIban.fromAccountNumber("000000000123456789");
        assertEquals(24, iban.length());
        assertTrue(iban.startsWith("SA"));
        assertTrue(SaudiIban.isValid(iban));
    }

    @Test
    void ibanValidatorAcceptsARealPublishedExampleAndRejectsATypo() {
        assertTrue(SaudiIban.isValid("SA0380000000608010167519"));   // official example IBAN for Saudi Arabia
        assertFalse(SaudiIban.isValid("SA0380000000608010167518"));  // one digit changed
    }

    @Test
    void walletBalanceCanNeverGoNegative() {
        Wallet wallet = new Wallet(null, "SA0000000000000000000000");
        wallet.credit(new BigDecimal("100.00"));
        wallet.debit(new BigDecimal("60.50"));
        assertEquals(new BigDecimal("39.50"), wallet.getBalance());

        BusinessException ex = assertThrows(BusinessException.class, () -> wallet.debit(new BigDecimal("39.51")));
        assertEquals("INSUFFICIENT_FUNDS", ex.getCode());
        assertEquals(new BigDecimal("39.50"), wallet.getBalance()); // unchanged after the failed debit
    }

    @Test
    void walletRejectsZeroOrNegativeAmounts() {
        Wallet wallet = new Wallet(null, "SA0000000000000000000000");
        assertThrows(IllegalArgumentException.class, () -> wallet.credit(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> wallet.debit(new BigDecimal("-5")));
    }

    @Test
    void monthlyPeriodUsesCalendarMonths() {
        // 31 Jan 2026 10:00 Riyadh + 1 month = 28 Feb 2026 10:00 Riyadh (2026 is not a leap year)
        Instant jan31 = Instant.parse("2026-01-31T07:00:00Z");
        assertEquals(Instant.parse("2026-02-28T07:00:00Z"), BillingPeriod.MONTHLY.addTo(jan31, RIYADH));
        assertEquals(Instant.parse("2027-01-31T07:00:00Z"), BillingPeriod.YEARLY.addTo(jan31, RIYADH));
    }
}
