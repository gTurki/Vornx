package com.example.vornx.whatsapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * WhatsApp through UltraMsg (https://docs.ultramsg.com): POST {base}/{instance}/messages/chat with
 * token, to, body (form-encoded). UltraMsg answers {"sent":"true","message":"ok","id":...} or {"error":...}.
 *
 * The token goes in the request BODY (not the URL), so it never lands in access logs; we never log it.
 */
@Slf4j
public class UltraMsgWhatsAppSender implements WhatsAppSender {

    private final RestClient client;
    private final String instanceId;
    private final String token;

    public UltraMsgWhatsAppSender(String baseUrl, String instanceId, String token) {
        this(RestClient.builder().requestFactory(requestFactoryWithTimeouts()), baseUrl, instanceId, token);
    }

    /** Also used by tests, with a fake server bound to the builder. */
    UltraMsgWhatsAppSender(RestClient.Builder builder, String baseUrl, String instanceId, String token) {
        this.client = builder.baseUrl(baseUrl).build();
        this.instanceId = instanceId.trim();
        this.token = token.trim();
        log.info("WhatsApp via UltraMsg, instance {}", this.instanceId);
    }

    @Override
    public void send(String toE164, String text) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        form.add("to", toE164);
        form.add("body", text);
        Map<?, ?> response;
        try {
            response = client.post()
                    .uri("/{instance}/messages/chat", instanceId)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            log.warn("UltraMsg request failed: {}", e.getMessage());
            throw new WhatsAppUnavailableException("WhatsApp could not be reached", e);
        }
        Object sent = response != null ? response.get("sent") : null;
        if (!"true".equals(String.valueOf(sent))) {
            // e.g. {"error":"Wrong token"} or the instance is not connected to a phone
            log.warn("UltraMsg did not send the message: {}", response != null ? response.get("error") : "empty answer");
            throw new WhatsAppUnavailableException("WhatsApp did not accept the message");
        }
        log.info("WhatsApp message accepted by UltraMsg, id={}", response.get("id"));
    }

    @Override
    public boolean isReal() {
        return true;
    }

    private static JdkClientHttpRequestFactory requestFactoryWithTimeouts() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return factory;
    }
}
