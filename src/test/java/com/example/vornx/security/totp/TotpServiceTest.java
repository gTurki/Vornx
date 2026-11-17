package com.example.vornx.security.totp;

import com.example.vornx.common.enums.Language;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.service.CustomerService;
import com.example.vornx.email.EmailService;
import com.example.vornx.email.EmailTemplates;
import com.example.vornx.email.EmailType;
import com.example.vornx.email.FrontendProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Turning the authenticator app on/off, logging in with it, and the recovery codes. Database = Mockito fake. */
class TotpServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-18T10:00:00Z");

    private final Customer customer = new Customer("VX1", "Turki", "A", "0551112233", "t@example.com", "hash", Language.AR);
    private final List<RecoveryCode> saved = new ArrayList<>();
    private RecoveryCodeRepository repo;
    private EmailService email;
    private PasswordEncoder encoder;
    private final SecretBox box = new SecretBox(new byte[32]);

    @BeforeEach
    void setUp() {
        repo = mock(RecoveryCodeRepository.class);
        email = mock(EmailService.class);
        encoder = mock(PasswordEncoder.class);
        when(repo.save(any())).thenAnswer(i -> {
            saved.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(repo.findByCustomerIdAndCodeHashAndUsedAtIsNull(any(), anyString())).thenAnswer(i -> saved.stream()
                .filter(r -> r.getUsedAt() == null && r.getCodeHash().equals(i.getArgument(1))).findFirst());
        when(repo.countByCustomerIdAndUsedAtIsNull(any())).thenAnswer(i -> saved.stream().filter(r -> r.getUsedAt() == null).count());
    }

    private TotpService at(Instant now) {
        CustomerService customers = mock(CustomerService.class);
        when(customers.getEntity(anyLong())).thenReturn(customer);
        return new TotpService(customers, repo, box, encoder, email,
                new EmailTemplates(new FrontendProperties("a", "p", "c")), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static String codeAt(String secret, Instant t) {
        return Totp.code(Totp.base32Decode(secret), Totp.step(t), 6);
    }

    @Test
    void setupThenEnableWithTheFirstCode() {
        TotpService.Setup s = at(T0).setup(1L);
        assertTrue(s.otpauthUri().startsWith("otpauth://totp/VORNX:t%40example.com?secret=" + s.secret()));
        assertFalse(customer.isTotpEnabled());
        assertFalse(customer.getTotpPendingSecret().contains(s.secret())); // stored encrypted

        BusinessException wrong = assertThrows(BusinessException.class, () -> at(T0).enable(1L, "000000".equals(codeAt(s.secret(), T0)) ? "111111" : "000000"));
        assertEquals("TOTP_CODE_INVALID", wrong.getCode());

        List<String> codes = at(T0).enable(1L, codeAt(s.secret(), T0));
        assertEquals(10, codes.size());
        assertTrue(codes.get(0).matches("[A-Z2-9]{5}-[A-Z2-9]{5}"));
        assertTrue(customer.isTotpEnabled());
        assertNull(customer.getTotpPendingSecret());
        verify(email).queue(eq("t@example.com"), eq(EmailType.SECURITY_ALERT), any());
        assertThrows(BusinessException.class, () -> at(T0).setup(1L)); // already on
    }

    @Test
    void loginCodeWorksOnceAndRecoveryCodesWorkOnce() {
        TotpService.Setup s = at(T0).setup(1L);
        List<String> recovery = at(T0).enable(1L, codeAt(s.secret(), T0));

        Instant later = T0.plusSeconds(90);
        String code = codeAt(s.secret(), later);
        assertTrue(at(later).checkLogin(customer, code));
        assertFalse(at(later).checkLogin(customer, code)); // the same code again: refused
        assertFalse(at(later).checkLogin(customer, codeAt(s.secret(), T0))); // the setup code: too old and used

        String r = recovery.get(3);
        assertTrue(at(later).checkLogin(customer, r.toLowerCase().replace("-", " ")));
        assertFalse(at(later).checkLogin(customer, r));
        assertFalse(at(later).checkLogin(customer, "ABCDE-FGHJK"));
    }

    @Test
    void turningOffNeedsThePassword() {
        TotpService.Setup s = at(T0).setup(1L);
        at(T0).enable(1L, codeAt(s.secret(), T0));
        when(encoder.matches("wrong", "hash")).thenReturn(false);
        when(encoder.matches("right", "hash")).thenReturn(true);

        assertEquals("WRONG_PASSWORD", assertThrows(BusinessException.class, () -> at(T0).disable(1L, "wrong")).getCode());
        assertTrue(customer.isTotpEnabled());
        at(T0).disable(1L, "right");
        assertFalse(customer.isTotpEnabled());
        assertNull(customer.getTotpSecret());
        verify(repo, times(2)).deleteAllForCustomer(1L); // on enable (old codes) and on disable
        assertEquals(Optional.empty(), Optional.ofNullable(customer.getTotpLastStep()));
    }
}
