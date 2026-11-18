package com.example.vornx.security.passkey;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Where passkeys may be used. A passkey is tied to the domain it was made on (the "relying party id"):
 * one made on vornx-customer.up.railway.app does NOT work on my.vornx.sa. Both empty = taken from
 * vornx.frontend.customer-url (the customer app), which is right as long as there is one customer domain.
 *
 * vornx.passkeys.rp-id=my.vornx.sa                   (PASSKEY_RP_ID)
 * vornx.passkeys.origins=https://my.vornx.sa         (PASSKEY_ORIGINS, comma separated)
 */
@ConfigurationProperties(prefix = "vornx.passkeys")
public record PasskeyProperties(String rpId, List<String> origins) {
}
