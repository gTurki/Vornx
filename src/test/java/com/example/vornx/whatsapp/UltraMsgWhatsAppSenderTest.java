package com.example.vornx.whatsapp;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** A FAKE UltraMsg server: checks exactly what we send, without a real instance or network. */
class UltraMsgWhatsAppSenderTest {

    private static final String BASE = "https://api.ultramsg.com";

    @Test
    void sendsTheRightRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer ultra = MockRestServiceServer.bindTo(builder).build();
        UltraMsgWhatsAppSender sender = new UltraMsgWhatsAppSender(builder, BASE, "instance123", "tok-secret");

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", "tok-secret");
        form.add("to", "+966551112233");
        form.add("body", "رمز 123456");
        ultra.expect(requestTo(BASE + "/instance123/messages/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formData(form))
                .andRespond(withSuccess("{\"sent\":\"true\",\"message\":\"ok\",\"id\":42}", MediaType.APPLICATION_JSON));

        sender.send("+966551112233", "رمز 123456");
        ultra.verify();
    }

    @Test
    void anUltraMsgRefusalThrows() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer ultra = MockRestServiceServer.bindTo(builder).build();
        UltraMsgWhatsAppSender sender = new UltraMsgWhatsAppSender(builder, BASE, "instance123", "wrong");

        ultra.expect(requestTo(BASE + "/instance123/messages/chat"))
                .andRespond(withSuccess("{\"error\":\"Wrong token. Please provide token that matches your instance\"}",
                        MediaType.APPLICATION_JSON));
        assertThrows(WhatsAppUnavailableException.class, () -> sender.send("+966551112233", "x"));
    }

    @Test
    void anHttpErrorThrows() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer ultra = MockRestServiceServer.bindTo(builder).build();
        UltraMsgWhatsAppSender sender = new UltraMsgWhatsAppSender(builder, BASE, "instance123", "tok");

        ultra.expect(requestTo(BASE + "/instance123/messages/chat")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThrows(WhatsAppUnavailableException.class, () -> sender.send("+966551112233", "x"));
    }
}
