package com.example.vornx.security.totp;

import com.example.vornx.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** "حسابي" > security: the authenticator app (two-step login with Google/Microsoft Authenticator). */
@Tag(name = "Customer - Security", description = "Authenticator app (TOTP) and recovery codes")
@RestController
@RequestMapping("/api/customer/security/totp")
@RequiredArgsConstructor
public class CustomerSecurityController {

    private final TotpService totp;

    public record EnableRequest(@NotBlank @Pattern(regexp = "\\s*\\d{6}\\s*", message = "must be 6 digits") String code) {
    }

    public record DisableRequest(@NotBlank @Size(max = 100) String password) {
    }

    public record RecoveryCodes(List<String> recoveryCodes) {
    }

    @GetMapping
    public TotpService.Status status(@AuthenticationPrincipal Jwt jwt) {
        return totp.status(AuthUser.from(jwt).id());
    }

    @Operation(summary = "Start: a new secret",
            description = "Show otpauthUri as a QR code (or the secret to type in). Then confirm with /enable.")
    @PostMapping("/setup")
    public TotpService.Setup setup(@AuthenticationPrincipal Jwt jwt) {
        return totp.setup(AuthUser.from(jwt).id());
    }

    @Operation(summary = "Confirm with the first code from the app",
            description = "Returns 10 one-time recovery codes: show them ONCE. 409 TOTP_CODE_INVALID / TOTP_SETUP_NEEDED.")
    @PostMapping("/enable")
    public RecoveryCodes enable(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EnableRequest req) {
        return new RecoveryCodes(totp.enable(AuthUser.from(jwt).id(), req.code().trim()));
    }

    @Operation(summary = "Turn off (needs the password)", description = "409 WRONG_PASSWORD.")
    @PostMapping("/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DisableRequest req) {
        totp.disable(AuthUser.from(jwt).id(), req.password());
    }
}
