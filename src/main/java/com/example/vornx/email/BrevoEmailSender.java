package com.example.vornx.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends through Brevo's HTTPS API (https://developers.brevo.com/reference/sendtransacemail).
 *
 * Why the API and not SMTP: Railway blocks outbound SMTP ports on its Free/Trial/Hobby plans,
 * while HTTPS (port 443) always works. The API is also faster and returns clear errors.
 *
 * Errors (bad key, unverified sender, Brevo down...) throw an exception: the outbox job then
 * retries later (1 min, 5 min, 30 min, 2 h) and finally marks the email FAILED.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "vornx.mail.mode", havingValue = "brevo")
public class BrevoEmailSender implements EmailSender {

    private static final Pattern NAME_AND_EMAIL = Pattern.compile("^\\s*(.*?)\\s*<\\s*([^>]+)\\s*>\\s*$");

    private final RestClient client;
    private final String senderName;
    private final String senderEmail;

    @Autowired
    public BrevoEmailSender(@Value("${vornx.mail.brevo.api-key:}") String apiKey,
                            @Value("${vornx.mail.brevo.url:https://api.brevo.com/v3/smtp/email}") String url,
                            @Value("${vornx.mail.from}") String from) {
        this(RestClient.builder().requestFactory(requestFactoryWithTimeouts()), apiKey, url, from);
    }

    /** Also used by tests, with a fake server bound to the builder. */
    BrevoEmailSender(RestClient.Builder builder, String apiKey, String url, String from) {
        if (!StringUtils.hasText(apiKey)) {
            // Fail at startup with a clear message, instead of failing every email later.
            throw new IllegalStateException("vornx.mail.mode=brevo but BREVO_API_KEY is not set");
        }
        Matcher m = NAME_AND_EMAIL.matcher(from);
        this.senderName = m.matches() && !m.group(1).isBlank() ? m.group(1) : "VORNX";
        this.senderEmail = m.matches() ? m.group(2).trim() : from.trim();
        this.client = builder
                .baseUrl(url)
                .defaultHeader("api-key", apiKey) // never logged
                .defaultHeader("accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("Email via Brevo API, sender: {} <{}>", senderName, senderEmail);
    }

    @Override
    public void send(OutboxEmail email) {
        Map<String, Object> body = Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", List.of(Map.of("email", email.getToAddress())),
                "subject", email.getSubject(),
                "htmlContent", email.getHtmlBody(),
                "textContent", email.getTextBody(),
                "tags", List.of(email.getType().name())); // filter by type in Brevo's dashboard

        // 2xx = accepted by Brevo. 4xx/5xx -> RestClient throws -> the outbox retries later.
        Map<?, ?> response = client.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        log.info("Email {} ({}) accepted by Brevo, messageId={}", email.getId(), email.getType(),
                response != null ? response.get("messageId") : null);
    }

    private static JdkClientHttpRequestFactory requestFactoryWithTimeouts() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(20));
        return factory;
    }
}
