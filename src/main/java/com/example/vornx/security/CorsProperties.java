package com.example.vornx.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** vornx.cors.allowed-origins = the websites allowed to call the API from a browser. */
@ConfigurationProperties(prefix = "vornx.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public List<String> allowedOrigins() {
        return allowedOrigins == null ? List.of() : allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
