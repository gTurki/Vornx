package com.example.vornx.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Where the 3 frontends live, so links in emails open the right portal. */
@ConfigurationProperties(prefix = "vornx.frontend")
public record FrontendProperties(String adminUrl, String partnerUrl, String customerUrl) {
}
