package com.example.vornx.security.passkey;

import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.util.SecureTokens;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.service.CustomerService;
import com.example.vornx.email.EmailService;
import com.example.vornx.email.EmailTemplates;
import com.example.vornx.email.EmailType;
import com.example.vornx.email.FrontendProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/**
 * Passkeys end to end with a fake phone: a real P-256 key signs real WebAuthn messages, and Yubico's library checks
 * them as it would for an iPhone. Database = in-memory lists behind Mockito.
 */
class PasskeyFlowTest {

    private static final String ORIGIN = "http://localhost:5175";
    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final List<Passkey> stored = new ArrayList<>();
    private final List<PasskeyChallenge> challengeRows = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(1);
    private EmailService email;
    private PasskeyChallengeRepository challenges;
    private PasskeyService service;

    @BeforeEach
    void setUp() {
        PasskeyRepository passkeys = mock(PasskeyRepository.class);
        when(passkeys.save(any())).thenAnswer(i -> {
            Passkey p = i.getArgument(0);
            ReflectionTestUtils.setField(p, "id", ids.getAndIncrement());
            stored.add(p);
            return p;
        });
        when(passkeys.findByCredentialId(anyString())).thenAnswer(i -> stored.stream()
                .filter(p -> p.getCredentialId().equals(i.getArgument(0))).findFirst());
        when(passkeys.findFirstByCustomerId(anyLong())).thenAnswer(i -> stored.stream()
                .filter(p -> p.getCustomerId().equals(i.getArgument(0))).findFirst());
        when(passkeys.findByCustomerIdOrderByCreatedAtAsc(anyLong())).thenAnswer(i -> stored.stream()
                .filter(p -> p.getCustomerId().equals(i.getArgument(0))).toList());
        when(passkeys.findByUserHandle(anyString())).thenAnswer(i -> stored.stream()
                .filter(p -> p.getUserHandle().equals(i.getArgument(0))).toList());
        when(passkeys.findByIdAndCustomerId(anyLong(), anyLong())).thenAnswer(i -> stored.stream()
                .filter(p -> p.getId().equals(i.getArgument(0)) && p.getCustomerId().equals(i.getArgument(1))).findFirst());
        org.mockito.Mockito.doAnswer(i -> stored.remove((Passkey) i.getArgument(0))).when(passkeys).delete(any());

        challenges = mock(PasskeyChallengeRepository.class);
        when(challenges.save(any())).thenAnswer(i -> { challengeRows.add(i.getArgument(0)); return i.getArgument(0); });
        when(challenges.lockByHash(anyString())).thenAnswer(i -> challengeRows.stream()
                .filter(c -> c.getTokenHash().equals(i.getArgument(0))).findFirst());

        CustomerService customers = mock(CustomerService.class);
        Customer khalid = customer(7L, "khalid@example.com");
        Customer other = customer(8L, "other@example.com");
        when(customers.getEntity(7L)).thenReturn(khalid);
        when(customers.getEntity(8L)).thenReturn(other);
        when(customers.findByEmail("khalid@example.com")).thenReturn(Optional.of(khalid));

        email = mock(EmailService.class);
        FrontendProperties frontend = new FrontendProperties("a", "p", ORIGIN);
        service = new PasskeyService(passkeys, challenges, customers, email, new EmailTemplates(frontend),
                Clock.fixed(NOW, ZoneOffset.UTC), new PasskeyProperties(null, List.of()), frontend);
    }

    private static Customer customer(Long id, String mail) {
        Customer c = mock(Customer.class);
        when(c.getId()).thenReturn(id);
        when(c.getEmail()).thenReturn(mail);
        when(c.getFirstName()).thenReturn("Khalid");
        when(c.getLastName()).thenReturn("Alotaibi");
        return c;
    }

    @Test
    void aPhoneAddsAPasskeyThenSignsInWithIt() throws Exception {
        FakePhone phone = new FakePhone();
        PasskeyService.Options reg = service.registerOptions(7L, "1.1.1.1");
        assertTrue(reg.options().contains("\"residentKey\":\"required\""));
        assertTrue(reg.options().contains("\"userVerification\":\"required\""));

        PasskeyService.PasskeyView view = service.register(7L, reg.challengeId(), phone.create(reg.options(), ORIGIN), "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0)");
        assertEquals("iPhone", view.name());
        assertTrue(view.synced());
        verify(email).queue(eq("khalid@example.com"), eq(EmailType.SECURITY_ALERT), any());

        PasskeyService.Options login = service.loginOptions("1.1.1.1");
        assertEquals(7L, service.login(login.challengeId(), phone.get(login.options(), ORIGIN)));
        assertEquals(NOW, stored.get(0).getLastUsedAt());
        assertEquals(1, stored.get(0).getSignCount());
    }

    @Test
    void aChallengeWorksOnlyOnce() throws Exception {
        FakePhone phone = register();
        PasskeyService.Options login = service.loginOptions("1.1.1.1");
        String signed = phone.get(login.options(), ORIGIN);
        service.login(login.challengeId(), signed);
        assertEquals("PASSKEY_CHALLENGE_INVALID", assertThrows(BusinessException.class,
                () -> service.login(login.challengeId(), signed)).getCode());
    }

    @Test
    void aPhishingSiteCantUseTheSignature() throws Exception {
        FakePhone phone = register();
        PasskeyService.Options login = service.loginOptions("1.1.1.1");
        assertEquals("PASSKEY_NOT_RECOGNIZED", assertThrows(BusinessException.class,
                () -> service.login(login.challengeId(), phone.get(login.options(), "https://vornx-login.example"))).getCode());
    }

    @Test
    void aDifferentKeyClaimingTheSameCredentialIsRefused() throws Exception {
        FakePhone phone = register();
        FakePhone thief = new FakePhone();
        thief.credentialId = phone.credentialId; // knows the id, not the private key
        thief.userHandle = phone.userHandle;
        PasskeyService.Options login = service.loginOptions("1.1.1.1");
        assertEquals("PASSKEY_NOT_RECOGNIZED", assertThrows(BusinessException.class,
                () -> service.login(login.challengeId(), thief.get(login.options(), ORIGIN))).getCode());
    }

    @Test
    void aRemovedPasskeyNoLongerSignsIn() throws Exception {
        FakePhone phone = register();
        service.remove(7L, stored.get(0).getId());
        verify(email, times(2)).queue(eq("khalid@example.com"), eq(EmailType.SECURITY_ALERT), any());
        PasskeyService.Options login = service.loginOptions("1.1.1.1");
        assertEquals("PASSKEY_NOT_RECOGNIZED", assertThrows(BusinessException.class,
                () -> service.login(login.challengeId(), phone.get(login.options(), ORIGIN))).getCode());
    }

    @Test
    void aRegistrationStepCantBeFinishedByAnotherCustomer() throws Exception {
        PasskeyService.Options reg = service.registerOptions(7L, "1.1.1.1");
        String created = new FakePhone().create(reg.options(), ORIGIN);
        assertEquals("PASSKEY_CHALLENGE_INVALID", assertThrows(BusinessException.class,
                () -> service.register(8L, reg.challengeId(), created, "ua")).getCode());
    }

    @Test
    void signInOptionsAreRateLimitedPerIp() {
        when(challenges.countByIpAddressAndCreatedAtAfter(eq("9.9.9.9"), any())).thenReturn(30L);
        assertEquals(429, assertThrows(ResponseStatusException.class, () -> service.loginOptions("9.9.9.9")).getStatusCode().value());
    }

    @Test
    void deviceNamesComeFromTheBrowser() {
        assertEquals("Android", PasskeyService.deviceName("Mozilla/5.0 (Linux; Android 15; Pixel 9)"));
        assertEquals("Mac", PasskeyService.deviceName("Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5)"));
        assertEquals("جهاز", PasskeyService.deviceName(null));
    }

    private FakePhone register() throws Exception {
        FakePhone phone = new FakePhone();
        PasskeyService.Options reg = service.registerOptions(7L, "1.1.1.1");
        service.register(7L, reg.challengeId(), phone.create(reg.options(), ORIGIN), "iPhone");
        return phone;
    }

    // ================= a fake phone (what Face ID + the Secure Enclave do) =================

    private static final class FakePhone {
        private final KeyPair keys;
        byte[] credentialId = SecureTokens.generate().getBytes(StandardCharsets.UTF_8);
        String userHandle;
        int counter = 0;

        FakePhone() throws Exception {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"));
            keys = g.generateKeyPair();
        }

        String create(String optionsJson, String origin) throws Exception {
            String challenge = field(optionsJson, "challenge");
            String rpId = field(optionsJson.substring(optionsJson.indexOf("\"rp\"")), "id");
            userHandle = field(optionsJson.substring(optionsJson.indexOf("\"user\"")), "id");
            byte[] clientData = clientData("webauthn.create", challenge, origin);
            ByteArrayOutputStream auth = new ByteArrayOutputStream();
            auth.write(sha256(rpId.getBytes(StandardCharsets.UTF_8)));
            auth.write(0x01 | 0x04 | 0x08 | 0x10 | 0x40); // user present, user verified (Face ID), backup eligible + backed up, key attached
            auth.write(ByteBuffer.allocate(4).putInt(counter).array());
            auth.write(new byte[16]); // aaguid
            auth.write(ByteBuffer.allocate(2).putShort((short) credentialId.length).array());
            auth.write(credentialId);
            auth.write(coseKey((ECPublicKey) keys.getPublic()));
            byte[] authData = auth.toByteArray();

            ByteArrayOutputStream att = new ByteArrayOutputStream(); // CBOR {"fmt":"none","attStmt":{},"authData":h'..'}
            att.write(0xA3);
            text(att, "fmt");
            text(att, "none");
            text(att, "attStmt");
            att.write(0xA0);
            text(att, "authData");
            att.write(0x59);
            att.write(ByteBuffer.allocate(2).putShort((short) authData.length).array());
            att.write(authData);

            String id = B64.encodeToString(credentialId);
            return "{\"id\":\"" + id + "\",\"rawId\":\"" + id + "\",\"type\":\"public-key\",\"response\":{\"clientDataJSON\":\""
                    + B64.encodeToString(clientData) + "\",\"attestationObject\":\"" + B64.encodeToString(att.toByteArray())
                    + "\",\"transports\":[\"internal\"]},\"clientExtensionResults\":{}}";
        }

        String get(String optionsJson, String origin) throws Exception {
            String challenge = field(optionsJson, "challenge");
            String rpId = field(optionsJson, "rpId");
            byte[] clientData = clientData("webauthn.get", challenge, origin);
            ByteArrayOutputStream auth = new ByteArrayOutputStream();
            auth.write(sha256(rpId.getBytes(StandardCharsets.UTF_8)));
            auth.write(0x01 | 0x04 | 0x08 | 0x10);
            auth.write(ByteBuffer.allocate(4).putInt(++counter).array());
            byte[] authData = auth.toByteArray();

            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initSign(keys.getPrivate());
            s.update(authData);
            s.update(sha256(clientData));
            String id = B64.encodeToString(credentialId);
            return "{\"id\":\"" + id + "\",\"rawId\":\"" + id + "\",\"type\":\"public-key\",\"response\":{\"clientDataJSON\":\""
                    + B64.encodeToString(clientData) + "\",\"authenticatorData\":\"" + B64.encodeToString(authData)
                    + "\",\"signature\":\"" + B64.encodeToString(s.sign()) + "\",\"userHandle\":\"" + userHandle
                    + "\"},\"clientExtensionResults\":{}}";
        }

        private static byte[] clientData(String type, String challenge, String origin) {
            return ("{\"type\":\"" + type + "\",\"challenge\":\"" + challenge + "\",\"origin\":\"" + origin
                    + "\",\"crossOrigin\":false}").getBytes(StandardCharsets.UTF_8);
        }

        /** COSE EC2 key: {1:2 (EC2), 3:-7 (ES256), -1:1 (P-256), -2:x, -3:y} */
        private static byte[] coseKey(ECPublicKey k) throws Exception {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            o.write(new byte[]{(byte) 0xA5, 0x01, 0x02, 0x03, 0x26, 0x20, 0x01, 0x21, 0x58, 0x20});
            o.write(fixed32(k.getW().getAffineX()));
            o.write(new byte[]{0x22, 0x58, 0x20});
            o.write(fixed32(k.getW().getAffineY()));
            return o.toByteArray();
        }

        private static byte[] fixed32(BigInteger v) {
            byte[] b = v.toByteArray();
            byte[] out = new byte[32];
            int n = Math.min(32, b.length);
            System.arraycopy(b, b.length - n, out, 32 - n, n);
            return out;
        }

        private static void text(ByteArrayOutputStream o, String s) throws Exception {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            o.write(0x60 + b.length);
            o.write(b);
        }

        private static byte[] sha256(byte[] b) throws Exception {
            return MessageDigest.getInstance("SHA-256").digest(b);
        }

        private static String field(String json, String name) {
            Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            if (!m.find()) throw new IllegalStateException("no " + name + " in " + json);
            return m.group(1);
        }
    }

}
