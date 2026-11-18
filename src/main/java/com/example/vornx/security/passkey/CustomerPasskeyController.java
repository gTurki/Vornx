package com.example.vornx.security.passkey;

import com.example.vornx.audit.Audited;
import com.example.vornx.security.AuthUser;
import com.example.vornx.security.session.ClientInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** "حسابي" > الأمان > Face ID والبصمة: add, list and remove the devices that can sign in with a passkey. */
@Tag(name = "Customer - Passkeys", description = "Face ID / fingerprint sign-in (WebAuthn)")
@RestController
@RequestMapping("/api/customer/passkeys")
@RequiredArgsConstructor
public class CustomerPasskeyController {

    private final PasskeyService passkeys;

    /** credential = JSON.stringify of what navigator.credentials.create() returned (see the customer app). */
    public record RegisterRequest(@NotBlank String challengeId, @NotBlank @Size(max = 20_000) String credential) {
    }

    @GetMapping
    public List<PasskeyService.PasskeyView> list(@AuthenticationPrincipal Jwt jwt) {
        return passkeys.list(AuthUser.from(jwt).id());
    }

    @Operation(summary = "Step 1: options for navigator.credentials.create()",
            description = "JSON.parse(options), turn the base64url fields into bytes, call create(), then POST the result to /passkeys.")
    @PostMapping("/options")
    public PasskeyService.Options options(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        return passkeys.registerOptions(AuthUser.from(jwt).id(), ClientInfo.from(http).ipAddress());
    }

    @Operation(summary = "Step 2: save the new passkey",
            description = "409 PASSKEY_FAILED / PASSKEY_EXISTS / PASSKEY_CHALLENGE_INVALID / PASSKEY_LIMIT.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Audited(action = "PASSKEY_ADDED", target = "PASSKEY", details = "#result.name()")
    public PasskeyService.PasskeyView register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RegisterRequest req,
                                               HttpServletRequest http) {
        return passkeys.register(AuthUser.from(jwt).id(), req.challengeId(), req.credential(), ClientInfo.from(http).userAgent());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Audited(action = "PASSKEY_REMOVED", target = "PASSKEY", targetId = "#id")
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        passkeys.remove(AuthUser.from(jwt).id(), id);
    }
}
