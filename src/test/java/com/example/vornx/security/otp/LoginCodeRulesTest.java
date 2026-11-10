package com.example.vornx.security.otp;

import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.util.SecureTokens;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.service.CustomerService;
import com.example.vornx.email.EmailService;
import com.example.vornx.email.EmailTemplates;
import com.example.vornx.email.EmailType;
import com.example.vornx.email.FrontendProperties;
import com.example.vornx.partner.service.PartnerUserService;
import com.example.vornx.platform.service.AdminUserService;
import com.example.vornx.security.UserType;
import com.example.vornx.security.auth.dto.LoginChallengeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

/** The rules of the login code: right code once, limited guesses, limited resends. Database = Mockito fake. */
class LoginCodeRulesTest {

    private static final Instant T0 = Instant.parse("2026-11-10T10:00:00Z");

    private LoginChallengeRepository repository;
    private EmailService email;
    private final AtomicReference<LoginChallenge> stored = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        repository = mock(LoginChallengeRepository.class);
        email = mock(EmailService.class);
        when(repository.save(any())).thenAnswer(i -> {
            stored.set(i.getArgument(0));
            return i.getArgument(0);
        });
        when(repository.lockByHash(anyString())).thenAnswer(i ->
                Optional.ofNullable(stored.get()).filter(c -> c.getTokenHash().equals(i.getArgument(0))));
    }

    private LoginCodeService at(Instant now) {
        CustomerService customers = mock(CustomerService.class);
        Customer c = mock(Customer.class);
        when(c.getEmail()).thenReturn("trroky007@gmail.com");
        when(c.getFirstName()).thenReturn("Turki");
        when(customers.getEntity(anyLong())).thenReturn(c);
        return new LoginCodeService(repository, customers, mock(PartnerUserService.class), mock(AdminUserService.class),
                email, new EmailTemplates(new FrontendProperties("a", "p", "c")), Clock.fixed(now, ZoneOffset.UTC),
                "email", true);
    }

    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    @Test
    void rightCodeLogsInOnceOnly() {
        LoginChallengeResponse r = at(T0).start(UserType.CUSTOMER, 7L, "1.2.3.4");
        assertEquals("CODE_REQUIRED", r.status());
        assertEquals("tr•••7@gmail.com", r.destination());
        verify(email).queue(eq("trroky007@gmail.com"), eq(EmailType.LOGIN_CODE), any());
        // only hashes are stored, never the token or the code
        assertEquals(SecureTokens.sha256(r.challengeToken()), stored.get().getTokenHash());
        assertTrue(!stored.get().getCodeHash().contains(r.devCode()));

        LoginChallenge ok = at(T0.plusSeconds(30)).verify(r.challengeToken(), r.devCode());
        assertEquals(7L, ok.getUserId());
        BusinessException again = assertThrows(BusinessException.class,
                () -> at(T0.plusSeconds(31)).verify(r.challengeToken(), r.devCode()));
        assertEquals("LOGIN_CHALLENGE_INVALID", again.getCode());
    }

    @Test
    void fiveWrongCodesBurnTheChallenge() {
        LoginChallengeResponse r = at(T0).start(UserType.CUSTOMER, 7L, null);
        LoginCodeService s = at(T0.plusSeconds(10));
        for (int left = 4; left >= 1; left--) {
            BusinessException e = assertThrows(BusinessException.class, () -> s.verify(r.challengeToken(), wrong(r.devCode())));
            assertEquals("CODE_INVALID", e.getCode());
            assertEquals(left, e.getDetails().get("attemptsLeft"));
        }
        assertEquals("LOGIN_CHALLENGE_INVALID",
                assertThrows(BusinessException.class, () -> s.verify(r.challengeToken(), wrong(r.devCode()))).getCode());
        // even the right code is useless now: back to the password
        assertEquals("LOGIN_CHALLENGE_INVALID",
                assertThrows(BusinessException.class, () -> s.verify(r.challengeToken(), r.devCode())).getCode());
    }

    @Test
    void codeExpiresAfterFiveMinutes() {
        LoginChallengeResponse r = at(T0).start(UserType.CUSTOMER, 7L, null);
        assertEquals(300, r.codeExpiresIn());
        assertEquals("CODE_EXPIRED", assertThrows(BusinessException.class,
                () -> at(T0.plus(Duration.ofMinutes(5))).verify(r.challengeToken(), r.devCode())).getCode());
    }

    @Test
    void resendWaitsAMinuteReplacesTheCodeAndIsLimited() {
        LoginChallengeResponse r = at(T0).start(UserType.CUSTOMER, 7L, null);
        BusinessException soon = assertThrows(BusinessException.class, () -> at(T0.plusSeconds(20)).resend(r.challengeToken()));
        assertEquals("CODE_RESEND_TOO_SOON", soon.getCode());
        assertEquals(40L, soon.getDetails().get("resendIn"));

        LoginChallengeResponse r2 = at(T0.plusSeconds(60)).resend(r.challengeToken());
        assertEquals(1, r2.resendsLeft());
        if (!r2.devCode().equals(r.devCode())) { // the old code no longer works (unless the dice gave the same one)
            assertEquals("CODE_INVALID", assertThrows(BusinessException.class,
                    () -> at(T0.plusSeconds(61)).verify(r.challengeToken(), r.devCode())).getCode());
        }
        LoginChallengeResponse r3 = at(T0.plusSeconds(130)).resend(r.challengeToken());
        assertEquals(0, r3.resendsLeft());
        assertEquals("CODE_RESEND_LIMIT", assertThrows(BusinessException.class,
                () -> at(T0.plusSeconds(200)).resend(r.challengeToken())).getCode());
        verify(email, times(3)).queue(anyString(), eq(EmailType.LOGIN_CODE), any());

        assertNotNull(at(T0.plusSeconds(201)).verify(r.challengeToken(), r3.devCode()));
    }

    @Test
    void tooManyLoginsInFifteenMinutesAreRefused() {
        when(repository.countByUserTypeAndUserIdAndCreatedAtAfter(eq(UserType.CUSTOMER), eq(7L), any())).thenReturn(5L);
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> at(T0).start(UserType.CUSTOMER, 7L, null));
        assertEquals(429, e.getStatusCode().value());
    }

    @Test
    void forgedOrMissingTokensAreRejected() {
        at(T0).start(UserType.CUSTOMER, 7L, null);
        assertEquals("LOGIN_CHALLENGE_INVALID", assertThrows(BusinessException.class,
                () -> at(T0).verify("lc_forged", "123456")).getCode());
        assertEquals("LOGIN_CHALLENGE_INVALID", assertThrows(BusinessException.class,
                () -> at(T0).verify("not-a-token", "123456")).getCode());
    }

    @Test
    void devCodeIsHiddenWhenNotExposed() {
        CustomerService customers = mock(CustomerService.class);
        Customer c = mock(Customer.class);
        when(c.getEmail()).thenReturn("a@b.sa");
        when(customers.getEntity(anyLong())).thenReturn(c);
        LoginCodeService prod = new LoginCodeService(repository, customers, mock(PartnerUserService.class),
                mock(AdminUserService.class), email, new EmailTemplates(new FrontendProperties("a", "p", "c")),
                Clock.fixed(T0, ZoneOffset.UTC), "email", false);
        assertNull(prod.start(UserType.CUSTOMER, 1L, null).devCode());
    }

    @Test
    void masksEmails() {
        assertEquals("tr•••7@gmail.com", LoginCodeService.maskEmail("trroky007@gmail.com"));
        assertEquals("a•••@b.sa", LoginCodeService.maskEmail("ab@b.sa"));
        assertEquals("•••", LoginCodeService.maskEmail("broken"));
    }
}
