package com.example.vornx.email;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** A FAKE Brevo server: checks exactly what we send, without a real account or network. */
class BrevoEmailSenderTest {

    private static final String URL = "https://api.brevo.com/v3/smtp/email";

    private static OutboxEmail email() {
        return new OutboxEmail(EmailType.PASSWORD_RESET, "fahad@example.com", "Reset your password",
                "<p>html</p>", "text", Instant.now());
    }

    @Test
    void sendsTheRightRequestToBrevo() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer brevo = MockRestServiceServer.bindTo(builder).build();
        BrevoEmailSender sender = new BrevoEmailSender(builder, "xkeysib-test", URL, "VORNX <no-reply@vornx.sa>");

        brevo.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "xkeysib-test"))
                .andExpect(jsonPath("$.sender.name").value("VORNX"))
                .andExpect(jsonPath("$.sender.email").value("no-reply@vornx.sa"))
                .andExpect(jsonPath("$.to[0].email").value("fahad@example.com"))
                .andExpect(jsonPath("$.subject").value("Reset your password"))
                .andExpect(jsonPath("$.htmlContent").value("<p>html</p>"))
                .andExpect(jsonPath("$.textContent").value("text"))
                .andExpect(jsonPath("$.tags[0]").value("PASSWORD_RESET"))
                .andRespond(withSuccess("{\"messageId\":\"<abc@smtp-relay.mailin.fr>\"}", MediaType.APPLICATION_JSON));

        sender.send(email());
        brevo.verify();
    }

    @Test
    void aBrevoErrorThrows_soTheOutboxRetriesLater() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer brevo = MockRestServiceServer.bindTo(builder).build();
        BrevoEmailSender sender = new BrevoEmailSender(builder, "wrong-key", URL, "VORNX <no-reply@vornx.sa>");

        brevo.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .body("{\"code\":\"unauthorized\",\"message\":\"Key not found\"}").contentType(MediaType.APPLICATION_JSON));

        assertThrows(HttpClientErrorException.Unauthorized.class, () -> sender.send(email()));
    }

    @Test
    void refusesToStartWithoutAnApiKey() {
        assertThrows(IllegalStateException.class,
                () -> new BrevoEmailSender(RestClient.builder(), "", URL, "VORNX <no-reply@vornx.sa>"));
    }

    @Test
    void acceptsAPlainSenderAddressToo() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer brevo = MockRestServiceServer.bindTo(builder).build();
        BrevoEmailSender sender = new BrevoEmailSender(builder, "k", URL, "vornx@outlook.sa");
        brevo.expect(jsonPath("$.sender.email").value("vornx@outlook.sa"))
                .andExpect(jsonPath("$.sender.name").value("VORNX"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        sender.send(email());
        brevo.verify();
    }
}
