package com.example.vornx.security.otp;

import com.example.vornx.common.exception.BusinessException;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The SMS (Firebase) channel of the login code, and its "send by email instead" fallback. */
class LoginCodeSmsRulesTest {

    private static final Instant T0 = Instant.parse("2026-11-10T10:00:00Z");

    private LoginChallengeRepository repository;
    private EmailService email;
    private FirebasePhoneVerifier firebase;
    private final AtomicReference<LoginChallenge> stored = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        repository = mock(LoginChallengeRepository.class);
        email = mock(EmailService.class);
        firebase = mock(FirebasePhoneVerifier.class);
        when(firebase.isConfigured()).thenReturn(true);
        when(repository.save(any())).thenAnswer(i -> {
            stored.set(i.getArgument(0));
            return i.getArgument(0);
        });
        when(repository.lockByHash(anyString())).thenAnswer(i ->
                Optional.ofNullable(stored.get()).filter(c -> c.getTokenHash().equals(i.getArgument(0))));
    }

    private LoginCodeService sms(Instant now, String phone) {
        CustomerService customers = mock(CustomerService.class);
        Customer c = mock(Customer.class);
        when(c.getEmail()).thenReturn("trroky007@gmail.com");
        when(c.getFirstName()).thenReturn("Turki");
        when(c.getPhone()).thenReturn(phone);
        when(customers.getEntity(anyLong())).thenReturn(c);
        return new LoginCodeService(repository, customers, mock(PartnerUserService.class), mock(AdminUserService.class),
                email, new EmailTemplates(new FrontendProperties("a", "p", "c")), firebase,
                Clock.fixed(now, ZoneOffset.UTC), "sms", true);
    }

    @Test
    void smsLoginGivesThePhoneToFirebaseAndSendsNoEmail() {
        LoginChallengeResponse r = sms(T0, "0551112233").start(UserType.CUSTOMER, 7L, null);
        assertEquals(LoginCodeChannel.SMS, r.channel());
        assertEquals("+966551112233", r.phoneNumber());
        assertEquals("+966 5•••••233", r.destination());
        assertNull(r.devCode()); // Firebase makes the code, we never know it
        verify(email, never()).queue(anyString(), any(), any());
    }

    @Test
    void firebaseProofForTheRightPhoneLogsIn() {
        LoginChallengeResponse r = sms(T0, "0551112233").start(UserType.CUSTOMER, 7L, null);
        when(firebase.verifiedPhone("good")).thenReturn("+966551112233");
        assertEquals(7L, sms(T0.plusSeconds(40), "0551112233").verifyPhone(r.challengeToken(), "good").getUserId());
        // single use
        assertEquals("LOGIN_CHALLENGE_INVALID", assertThrows(BusinessException.class,
                () -> sms(T0.plusSeconds(41), "0551112233").verifyPhone(r.challengeToken(), "good")).getCode());
    }

    @Test
    void someoneElsesPhoneIsRejectedAndCounted() {
        LoginChallengeResponse r = sms(T0, "0551112233").start(UserType.CUSTOMER, 7L, null);
        when(firebase.verifiedPhone("other")).thenReturn("+966500000000");
        BusinessException e = assertThrows(BusinessException.class,
                () -> sms(T0.plusSeconds(40), "0551112233").verifyPhone(r.challengeToken(), "other"));
        assertEquals("PHONE_MISMATCH", e.getCode());
        assertEquals(4, e.getDetails().get("attemptsLeft"));
    }

    @Test
    void forgedFirebaseTokenIsRejectedAndCounted() {
        LoginChallengeResponse r = sms(T0, "0551112233").start(UserType.CUSTOMER, 7L, null);
        when(firebase.verifiedPhone("forged")).thenThrow(new BusinessException("PHONE_VERIFICATION_FAILED", "x"));
        BusinessException e = assertThrows(BusinessException.class,
                () -> sms(T0.plusSeconds(40), "0551112233").verifyPhone(r.challengeToken(), "forged"));
        assertEquals("PHONE_VERIFICATION_FAILED", e.getCode());
        assertEquals(1, stored.get().getAttempts());
    }

    @Test
    void sendByEmailInsteadWorksAtOnceAndOnlyTheEmailCodeCountsAfter() {
        LoginChallengeResponse r = sms(T0, "0551112233").start(UserType.CUSTOMER, 7L, null);
        // a typed code can't pass while the login is on SMS
        assertEquals("CODE_INVALID", assertThrows(BusinessException.class,
                () -> sms(T0.plusSeconds(5), "0551112233").verify(r.challengeToken(), "123456")).getCode());

        LoginChallengeResponse byEmail = sms(T0.plusSeconds(10), "0551112233").resend(r.challengeToken()); // no 60 s wait
        assertEquals(LoginCodeChannel.EMAIL, byEmail.channel());
        assertEquals("tr•••7@gmail.com", byEmail.destination());
        assertNull(byEmail.phoneNumber());
        verify(email).queue(eq("trroky007@gmail.com"), eq(EmailType.LOGIN_CODE), any());

        // the Firebase route is closed now
        assertEquals("PHONE_VERIFICATION_FAILED", assertThrows(BusinessException.class,
                () -> sms(T0.plusSeconds(20), "0551112233").verifyPhone(r.challengeToken(), "good")).getCode());
        assertEquals(7L, sms(T0.plusSeconds(30), "0551112233").verify(r.challengeToken(), byEmail.devCode()).getUserId());
    }

    @Test
    void customerWithoutAUsablePhoneGetsEmail() {
        LoginChallengeResponse r = sms(T0, "12345").start(UserType.CUSTOMER, 7L, null);
        assertEquals(LoginCodeChannel.EMAIL, r.channel());
        verify(email).queue(anyString(), eq(EmailType.LOGIN_CODE), any());
    }

    @Test
    void smsModeWithoutFirebaseRefusesToStart() {
        when(firebase.isConfigured()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> sms(T0, "0551112233"));
    }

    @Test
    void phoneFormats() {
        assertEquals("+966551112233", LoginCodeService.toE164("0551112233"));
        assertEquals("+966551112233", LoginCodeService.toE164("055 111-2233"));
        assertEquals("+966551112233", LoginCodeService.toE164("+966551112233"));
        assertNull(LoginCodeService.toE164("0112345678"));
        assertNull(LoginCodeService.toE164(null));
    }
}
