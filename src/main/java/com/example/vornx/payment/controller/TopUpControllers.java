package com.example.vornx.payment.controller;

import com.example.vornx.payment.dto.TopUpDtos.StartTopUpRequest;
import com.example.vornx.payment.dto.TopUpDtos.TopUpOptions;
import com.example.vornx.payment.dto.TopUpDtos.TopUpResponse;
import com.example.vornx.payment.service.TopUpService;
import com.example.vornx.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

public final class TopUpControllers {

    private TopUpControllers() {
    }

    @Tag(name = "Customer - Wallet")
    @RestController
    @RequestMapping("/api/customer/wallet")
    @RequiredArgsConstructor
    public static class Customer {

        private final TopUpService topUpService;

        @GetMapping("/top-up-options")
        public TopUpOptions options() {
            return topUpService.options();
        }

        @Operation(summary = "Start a card top-up", description = "Returns checkoutUrl: send the customer to Moyasar's payment page.")
        @PostMapping("/top-ups")
        @ResponseStatus(HttpStatus.CREATED)
        public TopUpResponse start(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartTopUpRequest req) {
            return topUpService.start(AuthUser.from(jwt).id(), req.amount());
        }

        @Operation(summary = "Top-up status", description = "Called after returning from Moyasar. Verifies with Moyasar while still PENDING.")
        @GetMapping("/top-ups/{reference}")
        public TopUpResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String reference) {
            return topUpService.getForCustomer(AuthUser.from(jwt).id(), reference);
        }
    }

    /**
     * Moyasar's server calls this when an invoice is paid (the invoice's callback_url).
     * It is public, so its body is NOT trusted: we only read the invoice id and verify with Moyasar.
     * Always 200, so Moyasar doesn't keep retrying because of our internal issues.
     */
    @Tag(name = "Public - Payments")
    @RestController
    @RequestMapping("/api/public/payments/moyasar")
    @RequiredArgsConstructor
    public static class MoyasarCallback {

        private final TopUpService topUpService;

        @PostMapping("/callback")
        public Map<String, String> callback(@RequestBody(required = false) Map<String, Object> body) {
            Object id = body != null ? body.get("id") : null;
            try {
                topUpService.onProviderCallback(id instanceof String s ? s : null);
            } catch (RuntimeException e) {
                // the reconcile job will catch up
            }
            return Map.of("status", "received");
        }
    }
}
