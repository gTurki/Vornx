package com.example.vornx.platform.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Reads vornx.bootstrap.* from application properties. */
@ConfigurationProperties(prefix = "vornx.bootstrap")
public record BootstrapProperties(String adminName, String adminEmail, String adminPassword) {
}
