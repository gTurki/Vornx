package com.example.vornx.payment;

import com.example.vornx.payment.gateway.MoyasarClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** A FAKE Moyasar server: checks exactly what we send and that we read the answer right. */
class MoyasarClientTest {

    private static final String URL = "https://api.moyasar.com/v1";
    private static final String KEY = "sk_test_abc123";
    private static final String BASIC = "Basic " + Base64.getEncoder().encodeToString((KEY + ":").getBytes(StandardCharsets.UTF_8));

    @Test
    void createsAnInvoiceInHalalasWithBasicAuth() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer moyasar = MockRestServiceServer.bindTo(builder).build();
        MoyasarClient client = new MoyasarClient(builder, KEY, URL);

        moyasar.expect(requestTo(URL + "/invoices"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", BASIC))
                .andExpect(jsonPath("$.amount").value(15050))            // 150.50 SAR
                .andExpect(jsonPath("$.currency").value("SAR"))
                .andExpect(jsonPath("$.success_url").value("https://my.vornx.sa/wallet?topup=TU-1"))
                .andExpect(jsonPath("$.callback_url").value("https://api.vornx.sa/cb"))
                .andRespond(withSuccess("""
                        {"id":"inv-uuid-1","status":"initiated","amount":15050,"currency":"SAR",
                         "url":"https://checkout.moyasar.com/invoices/inv-uuid-1","payments":[],"metadata":null}
                        """, MediaType.APPLICATION_JSON));

        MoyasarClient.Invoice inv = client.createInvoice(15050, "SAR", "top-up", Instant.parse("2026-11-10T12:00:00Z"),
                "https://my.vornx.sa/wallet?topup=TU-1", "https://my.vornx.sa/wallet?topup=TU-1", "https://api.vornx.sa/cb");

        moyasar.verify();
        assertEquals("inv-uuid-1", inv.id());
        assertEquals("https://checkout.moyasar.com/invoices/inv-uuid-1", inv.url());
        assertFalse(inv.isPaid());
        assertTrue(client.isTestMode());
    }

    @Test
    void readsAPaidInvoice() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer moyasar = MockRestServiceServer.bindTo(builder).build();
        MoyasarClient client = new MoyasarClient(builder, KEY, URL);

        moyasar.expect(requestTo(URL + "/invoices/inv-uuid-1")).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", BASIC))
                .andRespond(withSuccess("{\"id\":\"inv-uuid-1\",\"status\":\"paid\",\"amount\":15050,\"currency\":\"SAR\"}",
                        MediaType.APPLICATION_JSON));

        MoyasarClient.Invoice inv = client.fetchInvoice("inv-uuid-1");
        assertTrue(inv.isPaid());
        assertEquals(15050, inv.amount());
    }

    @Test
    void refusesAPublishableKey() {
        assertThrows(IllegalStateException.class, () -> new MoyasarClient(RestClient.builder(), "pk_test_xyz", URL));
    }

    @Test
    void noKeyMeansNotConfigured() {
        assertFalse(new MoyasarClient(RestClient.builder(), "", URL).isConfigured());
    }
}
