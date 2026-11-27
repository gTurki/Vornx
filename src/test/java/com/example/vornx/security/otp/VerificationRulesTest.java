package com.example.vornx.security.otp;

import com.example.vornx.common.enums.Language;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.service.CustomerService;
import com.example.vornx.customer.service.PhoneVerificationPolicy;
import com.example.vornx.email.EmailService;
import com.example.vornx.email.EmailTemplates;
import com.example.vornx.email.EmailType;
import com.example.vornx.email.FrontendProperties;
import com.example.vornx.partner.service.PartnerUserService;
import com.example.vornx.platform.service.AdminUserService;
import com.example.vornx.security.UserType;
import com.example.vornx.security.auth.dto.LoginChallengeResponse;
import com.example.vornx.security.totp.TotpService;
import com.example.vornx.whatsapp.WhatsAppSender;
import com.example.vornx.whatsapp.WhatsAppUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Sign-up email check, mobile check by WhatsApp, and the authenticator app as the login's second step. */
class VerificationRulesTest {

    private static final Instant T0 = Instant.parse("2026-11-18T10:00:00Z");

    private LoginChallengeRepository repository;
    private EmailService email;
    private WhatsAppSender whatsApp;
    private TotpService totp;
    private PhoneVerificationPolicy policy;
    private Customer customer;
    private final Map<String, LoginChallenge> stored = new HashMap<>();

    @BeforeEach
    void setUp() {
        repository = mock(LoginChallengeRepository.class);
        email = mock(EmailService.class);
        whatsApp = mock(WhatsAppSender.class);
        totp = mock(TotpService.class);
        policy = mock(PhoneVerificationPolicy.class);
        when(policy.isEnabled()).thenReturn(true);
        customer = new Customer("VX1", "Turki", "A", "0551112233", "turki.demo@example.com", "hash", Language.AR);
        when(repository.save(any())).thenAnswer(i -> {
            LoginChallenge c = i.getArgument(0);
            stored.put(c.getTokenHash(), c);
            return c;
        });
        when(repository.lockByHash(anyString())).thenAnswer(i -> Optional.ofNullable(stored.get(i.<String>getArgument(0))));
    }

    private LoginCodeService at(Instant now, String mode) {
        CustomerService customers = mock(CustomerService.class);
        when(customers.getEntity(anyLong())).thenReturn(customer);
        return new LoginCodeService(repository, customers, mock(PartnerUserService.class), mock(AdminUserService.class),
                email, new EmailTemplates(new FrontendProperties("a", "p", "c")), mock(FirebasePhoneVerifier.class),
                totp, whatsApp, policy, Clock.fixed(now, ZoneOffset.UTC), mode, true);
    }

    private LoginCodeService at(Instant now) {
        return at(now, "email");
    }

    @Test
    void signupCodeConfirmsTheEmailAndSendsTheWelcome() {
        LoginChallengeResponse r = at(T0).startSignup(7L, null);
        assertEquals(LoginCodeChannel.EMAIL, r.channel());
        assertEquals(CodePurpose.SIGNUP, r.purpose());
        verify(email).queue(eq("turki.demo@example.com"), eq(EmailType.EMAIL_VERIFICATION), any());
        verify(email, never()).queue(anyString(), eq(EmailType.CUSTOMER_WELCOME), any());

        at(T0.plusSeconds(20)).verify(r.challengeToken(), r.devCode());
        assertTrue(customer.isEmailVerified());
        verify(email).queue(eq("turki.demo@example.com"), eq(EmailType.CUSTOMER_WELCOME), any());
    }

    @Test
    void anUnconfirmedEmailAlwaysGetsAnEmailCode_evenWithTwoStepLoginOff() {
        LoginCodeService off = at(T0, "off");
        assertTrue(off.customerCodeRequired(customer));
        LoginChallengeResponse r = off.start(UserType.CUSTOMER, 7L, null);
        assertEquals(LoginCodeChannel.EMAIL, r.channel());
        off.verify(r.challengeToken(), r.devCode());
        assertTrue(customer.isEmailVerified());
        assertFalse(off.customerCodeRequired(customer)); // now password only, as configured
    }

    @Test
    void whatsAppCodeConfirmsTheMobile() {
        customer.markEmailVerified(T0);
        LoginChallengeResponse r = at(T0).startPhone(7L, null);
        assertEquals(LoginCodeChannel.WHATSAPP, r.channel());
        assertEquals("+966 5•••••233", r.destination());
        assertNull(r.phoneNumber());
        verify(whatsApp).send(eq("+966551112233"), contains(r.devCode()));

        at(T0.plusSeconds(10)).verifyPhoneCode(r.challengeToken(), r.devCode(), 7L);
        assertTrue(customer.isPhoneVerified());
        assertEquals("PHONE_ALREADY_VERIFIED",
                assertThrows(BusinessException.class, () -> at(T0).startPhone(7L, null)).getCode());
    }

    @Test
    void aMobileCodeCanNeverLogInAndALoginCodeCanNeverConfirmTheMobile() {
        customer.markEmailVerified(T0);
        LoginChallengeResponse phone = at(T0).startPhone(7L, null);
        assertEquals("LOGIN_CHALLENGE_INVALID", assertThrows(BusinessException.class,
                () -> at(T0).verify(phone.challengeToken(), phone.devCode())).getCode());
        assertEquals("LOGIN_CHALLENGE_INVALID", assertThrows(BusinessException.class,
                () -> at(T0).resend(phone.challengeToken())).getCode());
        // another customer can't use it either
        assertEquals("LOGIN_CHALLENGE_INVALID", assertThrows(BusinessException.class,
                () -> at(T0).verifyPhoneCode(phone.challengeToken(), phone.devCode(), 8L)).getCode());

        LoginChallengeResponse login = at(T0).start(UserType.CUSTOMER, 7L, null);
        assertEquals("LOGIN_CHALLENGE_INVALID", assertThrows(BusinessException.class,
                () -> at(T0).verifyPhoneCode(login.challengeToken(), login.devCode(), 7L)).getCode());
        assertFalse(customer.isPhoneVerified());
    }

    @Test
    void whatsAppTextStartsWithTheCodeInArabicThenEnglish() {
        String text = LoginCodeService.whatsAppText("482910", 5);
        assertEquals("482910 هو رمز التحقق الخاص بك في VORNX. صالح لمدة 5 دقائق. لحماية حسابك، لا تشارك هذا الرمز مع أي أحد.\n\n"
                + "[VORNX] 482910 is your verification code, valid for 5 minutes. To protect your account, never share this code with anyone.",
                text);
    }

    @Test
    void noMobileCheckWhenTheFeatureIsOff() {
        when(policy.isEnabled()).thenReturn(false);
        assertEquals("PHONE_VERIFICATION_OFF",
                assertThrows(BusinessException.class, () -> at(T0).startPhone(7L, null)).getCode());
        verify(whatsApp, never()).send(anyString(), anyString());
    }

    @Test
    void aWhatsAppFailureIsNotSwallowed() {
        doThrow(new WhatsAppUnavailableException("down")).when(whatsApp).send(anyString(), anyString());
        assertThrows(WhatsAppUnavailableException.class, () -> at(T0).startPhone(7L, null));
    }

    @Test
    void withTheAuthenticatorAppTheLoginAsksForItsCodeAndSendsNothing() {
        customer.markEmailVerified(T0);
        customer.setTotpSecret("v1:x");
        customer.setTotpEnabledAt(T0);
        LoginChallengeResponse r = at(T0, "off").start(UserType.CUSTOMER, 7L, null);
        assertEquals(LoginCodeChannel.TOTP, r.channel());
        assertNull(r.destination());
        assertNull(r.devCode());
        assertEquals(0, r.resendsLeft());
        verify(email, never()).queue(anyString(), any(), any());
        assertEquals("CODE_RESEND_NOT_AVAILABLE", assertThrows(BusinessException.class,
                () -> at(T0.plusSeconds(90)).resend(r.challengeToken())).getCode());

        when(totp.checkLogin(customer, "111111")).thenReturn(false);
        when(totp.checkLogin(customer, "222222")).thenReturn(true);
        BusinessException wrong = assertThrows(BusinessException.class, () -> at(T0).verify(r.challengeToken(), "111111"));
        assertEquals("CODE_INVALID", wrong.getCode());
        assertEquals(4, wrong.getDetails().get("attemptsLeft"));
        assertEquals(7L, at(T0).verify(r.challengeToken(), "222222").getUserId());
    }

    @Test
    void theAuthenticatorAppIsNotUsedBeforeTheEmailIsConfirmed() {
        customer.setTotpSecret("v1:x");
        customer.setTotpEnabledAt(T0);
        assertEquals(LoginCodeChannel.EMAIL, at(T0).start(UserType.CUSTOMER, 7L, null).channel());
    }
}
