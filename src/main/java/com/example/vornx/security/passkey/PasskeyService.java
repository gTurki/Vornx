package com.example.vornx.security.passkey;

import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.common.util.SecureTokens;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.service.CustomerService;
import com.example.vornx.email.EmailService;
import com.example.vornx.email.EmailTemplates;
import com.example.vornx.email.EmailType;
import com.example.vornx.email.FrontendProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.PublicKeyCredentialParameters;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Passkeys: sign in with Face ID / Touch ID / fingerprint / Windows Hello (WebAuthn).
 *
 * Add (logged in, "حسابي"): registerOptions -> the phone creates a key pair after Face ID -> register saves the PUBLIC key.
 * Sign in (public):        loginOptions -> the phone signs our random challenge after Face ID -> login checks the signature.
 *
 * A passkey replaces the password AND the email code: it is something you have (the phone) plus something you are
 * (Face ID), and it can't be phished because the phone only signs for our domain. Yubico's library does the checks.
 */
@Slf4j
@Service
@Transactional
public class PasskeyService {

    static final Duration CHALLENGE_VALIDITY = Duration.ofMinutes(5);
    static final int MAX_PER_CUSTOMER = 10;
    /** Sign-in options are public: limit how many one IP can ask for. */
    static final int LOGIN_OPTIONS_PER_IP_PER_10_MIN = 30;
    private static final String PREFIX = "pkc_";

    private final PasskeyRepository passkeys;
    private final PasskeyChallengeRepository challenges;
    private final CustomerService customers;
    private final EmailService email;
    private final EmailTemplates templates;
    private final Clock clock;
    private final RelyingParty rp;
    private final SecureRandom random = new SecureRandom();

    public PasskeyService(PasskeyRepository passkeys, PasskeyChallengeRepository challenges, CustomerService customers,
                          EmailService email, EmailTemplates templates, Clock clock,
                          PasskeyProperties props, FrontendProperties frontend) {
        this.passkeys = passkeys;
        this.challenges = challenges;
        this.customers = customers;
        this.email = email;
        this.templates = templates;
        this.clock = clock;
        URI customerApp = URI.create(frontend.customerUrl());
        String rpId = StringUtils.hasText(props.rpId()) ? props.rpId().trim() : customerApp.getHost();
        Set<String> configured = props.origins() == null ? Set.of()
                : props.origins().stream().map(String::trim).filter(StringUtils::hasText).collect(Collectors.toSet());
        Set<String> origins = configured.isEmpty() ? Set.of(origin(customerApp)) : configured;
        this.rp = RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder().id(rpId).name("VORNX").build())
                .credentialRepository(new Credentials())
                .origins(origins)
                // what phones use (Face ID / Android: ES256, Windows Hello: RS256); also avoids startup warnings
                // about newer algorithms this Java can't verify
                .preferredPubkeyParams(List.of(PublicKeyCredentialParameters.ES256, PublicKeyCredentialParameters.EdDSA,
                        PublicKeyCredentialParameters.RS256))
                .clock(clock)
                .build();
        log.info("Passkeys: relying party id {} for {}", rpId, origins);
    }

    // =============== records the API returns ===============

    /** options = the JSON to give to navigator.credentials.create() / get() ({"publicKey": {...}}). */
    public record Options(String challengeId, String options) {
    }

    public record PasskeyView(Long id, String name, boolean synced, Instant createdAt, Instant lastUsedAt) {
        static PasskeyView from(Passkey p) {
            return new PasskeyView(p.getId(), p.getName(), p.isBackedUp(), p.getCreatedAt(), p.getLastUsedAt());
        }
    }

    // =============== add a passkey (logged in) ===============

    public Options registerOptions(Long customerId, String ipAddress) {
        Customer c = customers.getEntity(customerId);
        if (passkeys.countByCustomerId(customerId) >= MAX_PER_CUSTOMER) {
            throw new BusinessException("PASSKEY_LIMIT", "You can add up to " + MAX_PER_CUSTOMER + " devices");
        }
        // the same random handle for all of a customer's passkeys, so the phone replaces (not duplicates) its own
        String handle = passkeys.findFirstByCustomerId(customerId).map(Passkey::getUserHandle).orElseGet(this::newHandle);
        PublicKeyCredentialCreationOptions request = rp.startRegistration(StartRegistrationOptions.builder()
                .user(UserIdentity.builder()
                        .name(c.getEmail())
                        .displayName((c.getFirstName() + " " + c.getLastName()).trim())
                        .id(fromB64(handle))
                        .build())
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)            // sign in without typing the email
                        .userVerification(UserVerificationRequirement.REQUIRED) // Face ID / fingerprint / PIN every time
                        .build())
                .timeout(CHALLENGE_VALIDITY.toMillis())
                .build());
        try {
            return save(PasskeyChallenge.Purpose.REGISTER, customerId, request.toJson(), request.toCredentialsCreateJson(), ipAddress);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public PasskeyView register(Long customerId, String challengeId, String credentialJson, String userAgent) {
        PasskeyChallenge ch = take(challengeId, PasskeyChallenge.Purpose.REGISTER);
        if (!customerId.equals(ch.getCustomerId())) {
            throw invalid();
        }
        RegistrationResult result;
        PublicKeyCredentialCreationOptions request;
        try {
            request = PublicKeyCredentialCreationOptions.fromJson(ch.getRequestJson());
            result = rp.finishRegistration(FinishRegistrationOptions.builder()
                    .request(request)
                    .response(PublicKeyCredential.parseRegistrationResponseJson(credentialJson))
                    .build());
        } catch (RegistrationFailedException | IOException | RuntimeException e) {
            log.info("Passkey registration refused for customer {}: {}", customerId, e.getMessage());
            throw new BusinessException("PASSKEY_FAILED", "This device could not be added. Try again.");
        }
        String credentialId = result.getKeyId().getId().getBase64Url();
        if (passkeys.findByCredentialId(credentialId).isPresent()) {
            throw new BusinessException("PASSKEY_EXISTS", "This device is already added");
        }
        String name = deviceName(userAgent);
        Passkey saved = passkeys.save(new Passkey(customerId, credentialId, request.getUser().getId().getBase64Url(),
                result.getPublicKeyCose().getBytes(), result.getSignatureCount(), name, result.isBackedUp()));
        Customer c = customers.getEntity(customerId);
        email.queue(c.getEmail(), EmailType.SECURITY_ALERT, templates.passkeyChanged(c.getFirstName(), name, true));
        return PasskeyView.from(saved);
    }

    @Transactional(readOnly = true)
    public List<PasskeyView> list(Long customerId) {
        return passkeys.findByCustomerIdOrderByCreatedAtAsc(customerId).stream().map(PasskeyView::from).toList();
    }

    public void remove(Long customerId, Long passkeyId) {
        Passkey p = passkeys.findByIdAndCustomerId(passkeyId, customerId) // never another customer's
                .orElseThrow(() -> new ResourceNotFoundException("Passkey", passkeyId));
        passkeys.delete(p);
        Customer c = customers.getEntity(customerId);
        email.queue(c.getEmail(), EmailType.SECURITY_ALERT, templates.passkeyChanged(c.getFirstName(), p.getName(), false));
    }

    // =============== sign in (public) ===============

    public Options loginOptions(String ipAddress) {
        Instant now = Instant.now(clock);
        if (ipAddress != null && challenges.countByIpAddressAndCreatedAtAfter(ipAddress, now.minus(Duration.ofMinutes(10)))
                >= LOGIN_OPTIONS_PER_IP_PER_10_MIN) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Try again later.");
        }
        // no email asked: the phone shows the VORNX passkeys it has, the customer picks one with Face ID
        AssertionRequest request = rp.startAssertion(StartAssertionOptions.builder()
                .userVerification(UserVerificationRequirement.REQUIRED)
                .timeout(CHALLENGE_VALIDITY.toMillis())
                .build());
        try {
            return save(PasskeyChallenge.Purpose.LOGIN, null, request.toJson(), request.toCredentialsGetJson(), ipAddress);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Checks the phone's signature. Returns the customer it belongs to (AuthService checks the account and logs in). */
    @Transactional(noRollbackFor = BusinessException.class)
    public Long login(String challengeId, String credentialJson) {
        PasskeyChallenge ch = take(challengeId, PasskeyChallenge.Purpose.LOGIN);
        AssertionResult result;
        try {
            result = rp.finishAssertion(FinishAssertionOptions.builder()
                    .request(AssertionRequest.fromJson(ch.getRequestJson()))
                    .response(PublicKeyCredential.parseAssertionResponseJson(credentialJson))
                    .build());
        } catch (AssertionFailedException | IOException | RuntimeException e) {
            // also: a passkey the customer removed in "حسابي" (the phone still offers it)
            log.info("Passkey sign-in refused: {}", e.getMessage());
            throw new BusinessException("PASSKEY_NOT_RECOGNIZED",
                    "This passkey isn't recognised. Sign in with your password, then add the device again.");
        }
        if (!result.isSuccess() || !result.isUserVerified()) {
            throw new BusinessException("PASSKEY_NOT_RECOGNIZED", "This passkey isn't recognised.");
        }
        Passkey p = passkeys.findByCredentialId(result.getCredentialId().getBase64Url())
                .orElseThrow(() -> new BusinessException("PASSKEY_NOT_RECOGNIZED", "This passkey isn't recognised."));
        p.used(result.getSignatureCount(), result.isBackedUp(), Instant.now(clock));
        return p.getCustomerId();
    }

    // =============== housekeeping ===============

    @Scheduled(cron = "0 45 3 * * *", zone = "Asia/Riyadh")
    public void deleteOld() {
        int n = challenges.deleteExpiredBefore(Instant.now(clock).minus(Duration.ofHours(1)));
        if (n > 0) {
            log.info("Deleted {} old passkey challenge(s)", n);
        }
    }

    // =============== helpers ===============

    private Options save(PasskeyChallenge.Purpose purpose, Long customerId, String requestJson, String optionsJson, String ip) {
        String raw = PREFIX + SecureTokens.generate();
        challenges.save(new PasskeyChallenge(SecureTokens.sha256(raw), purpose, customerId, requestJson, ip,
                Instant.now(clock).plus(CHALLENGE_VALIDITY)));
        return new Options(raw, optionsJson);
    }

    /** One use only: marked used even when the check then fails, so a challenge can't be retried. */
    private PasskeyChallenge take(String challengeId, PasskeyChallenge.Purpose purpose) {
        Instant now = Instant.now(clock);
        PasskeyChallenge ch = (challengeId != null && challengeId.startsWith(PREFIX)
                ? challenges.lockByHash(SecureTokens.sha256(challengeId)) : Optional.<PasskeyChallenge>empty())
                .filter(c -> c.getPurpose() == purpose && c.isUsable(now))
                .orElseThrow(PasskeyService::invalid);
        ch.use(now);
        return ch;
    }

    private static BusinessException invalid() {
        return new BusinessException("PASSKEY_CHALLENGE_INVALID", "This step expired. Try again.");
    }

    private String newHandle() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return new ByteArray(b).getBase64Url();
    }

    private static ByteArray fromB64(String b64) {
        try {
            return ByteArray.fromBase64Url(b64);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String origin(URI u) {
        return u.getScheme() + "://" + u.getHost() + (u.getPort() == -1 ? "" : ":" + u.getPort());
    }

    /** A friendly name for "حسابي" and the security email. */
    static String deviceName(String ua) {
        if (ua == null) return "جهاز";
        if (ua.contains("iPhone")) return "iPhone";
        if (ua.contains("iPad")) return "iPad";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("Macintosh")) return "Mac";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("CrOS")) return "Chromebook";
        if (ua.contains("Linux")) return "Linux";
        return "جهاز";
    }

    // =============== what the library asks about stored passkeys ===============

    /** "username" for the library = the customer's email (shown by the phone when choosing a passkey). */
    private class Credentials implements CredentialRepository {

        @Override
        public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
            // used to tell the phone which passkeys exist already, so it doesn't add the same one twice
            return customers.findByEmail(username).stream()
                    .flatMap(c -> passkeys.findByCustomerIdOrderByCreatedAtAsc(c.getId()).stream())
                    .map(p -> PublicKeyCredentialDescriptor.builder().id(fromB64(p.getCredentialId())).build())
                    .collect(Collectors.toSet());
        }

        @Override
        public Optional<ByteArray> getUserHandleForUsername(String username) {
            return customers.findByEmail(username)
                    .flatMap(c -> passkeys.findFirstByCustomerId(c.getId()))
                    .map(p -> fromB64(p.getUserHandle()));
        }

        @Override
        public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
            return passkeys.findByUserHandle(userHandle.getBase64Url()).stream().findFirst()
                    .map(p -> customers.getEntity(p.getCustomerId()).getEmail());
        }

        @Override
        public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
            return passkeys.findByCredentialId(credentialId.getBase64Url())
                    .filter(p -> p.getUserHandle().equals(userHandle.getBase64Url()))
                    .map(Credentials::toRegistered);
        }

        @Override
        public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
            return passkeys.findByCredentialId(credentialId.getBase64Url()).map(Credentials::toRegistered)
                    .stream().collect(Collectors.toSet());
        }

        private static RegisteredCredential toRegistered(Passkey p) {
            return RegisteredCredential.builder()
                    .credentialId(fromB64(p.getCredentialId()))
                    .userHandle(fromB64(p.getUserHandle()))
                    .publicKeyCose(new ByteArray(p.getPublicKeyCose()))
                    .signatureCount(p.getSignCount())
                    .backupEligible(p.isBackedUp() ? Boolean.TRUE : null)
                    .backupState(p.isBackedUp())
                    .build();
        }
    }
}
