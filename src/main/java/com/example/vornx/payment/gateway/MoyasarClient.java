package com.example.vornx.payment.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Talks to Moyasar's API (https://docs.moyasar.com) with our SECRET key.
 *
 * Auth: HTTP Basic, the secret key as the username and an empty password.
 * Amounts: Moyasar counts in the smallest unit - 10.50 SAR is 1050 halalas.
 *
 * If MOYASAR_SECRET_KEY is not set, the client is "not configured" and card top-up is simply unavailable.
 * The key never leaves the backend and is never logged.
 */
@Slf4j
@Component
public class MoyasarClient {

    /** What we need from Moyasar's invoice object. Unknown fields are ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Invoice(String id, String status, long amount, String currency, String url,
                          @JsonProperty("expired_at") String expiredAt) {
        public boolean isPaid() {
            return "paid".equals(status);
        }

        /** Final "no money" states. ("initiated" / "on_hold" are still open.) */
        public boolean isDead() {
            return "failed".equals(status) || "canceled".equals(status) || "expired".equals(status)
                    || "voided".equals(status) || "refunded".equals(status);
        }
    }

    private final RestClient client;
    private final boolean configured;
    private final boolean testMode;

    @Autowired
    public MoyasarClient(@Value("${vornx.payments.moyasar.secret-key:}") String secretKey,
                         @Value("${vornx.payments.moyasar.url:https://api.moyasar.com/v1}") String url) {
        this(RestClient.builder().requestFactory(requestFactoryWithTimeouts()), secretKey, url);
    }

    /** Also used by tests, with a fake server bound to the builder. */
    public MoyasarClient(RestClient.Builder builder, String secretKey, String url) {
        this.configured = StringUtils.hasText(secretKey);
        this.testMode = configured && secretKey.startsWith("sk_test_");
        if (configured && !secretKey.startsWith("sk_")) {
            // A publishable key (pk_) here would fail every call: say it clearly at startup.
            throw new IllegalStateException("MOYASAR_SECRET_KEY must be a secret key (sk_test_... or sk_live_...)");
        }
        String basic = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        this.client = builder
                .baseUrl(url)
                .defaultHeader("Authorization", "Basic " + basic)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
        if (configured) {
            log.info("Moyasar payments enabled ({} mode)", testMode ? "TEST" : "LIVE");
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    public boolean isTestMode() {
        return testMode;
    }

    /**
     * POST /invoices -> a hosted payment page (invoice.url) for this amount.
     *
     * @param successUrl where Moyasar sends the customer after paying
     * @param backUrl    where the "back" button on Moyasar's page goes
     * @param callbackUrl Moyasar POSTs the invoice here when paid (optional; needs a public https URL)
     */
    public Invoice createInvoice(long amountHalalas, String currency, String description, Instant expiresAt,
                                 String successUrl, String backUrl, String callbackUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountHalalas);
        body.put("currency", currency);
        body.put("description", description);
        body.put("expired_at", expiresAt.toString());
        body.put("success_url", successUrl);
        body.put("back_url", backUrl);
        if (StringUtils.hasText(callbackUrl)) {
            body.put("callback_url", callbackUrl);
        }
        return client.post().uri("/invoices").contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(Invoice.class);
    }

    /** GET /invoices/{id}: the source of truth for "was it paid?". */
    public Invoice fetchInvoice(String id) {
        return client.get().uri("/invoices/{id}", id).retrieve().body(Invoice.class);
    }

    private static JdkClientHttpRequestFactory requestFactoryWithTimeouts() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return factory;
    }
}
