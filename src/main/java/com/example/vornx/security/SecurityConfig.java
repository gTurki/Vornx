package com.example.vornx.security;

import com.example.vornx.partner.service.ApiKeyService;
import com.example.vornx.security.device.ApiKeyAuthenticationFilter;
import com.example.vornx.security.device.DevicePrincipal;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Who can call what:
 *   /api/public/**   -> anyone (landing page, login, register)
 *   /api/admin/**    -> admins only       (token with TYPE_ADMIN)
 *   /api/partner/**  -> partner staff only (token with TYPE_PARTNER_USER)
 *   /api/customer/** -> customers only     (token with TYPE_CUSTOMER)
 *   /api/devices/**  -> readers/locks, with an "X-API-Key" header (separate chain below)
 *   anything else    -> blocked
 */
@Configuration
@EnableMethodSecurity // allows @PreAuthorize("hasRole('SUPER_ADMIN')") on methods later
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProperties jwtProperties;

    /**
     * Chain 1 (checked first): only for /api/devices/**. Devices use an API key, not a JWT.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain deviceSecurityFilterChain(HttpSecurity http, ApiKeyService apiKeyService) throws Exception {
        http
                .securityMatcher("/api/devices/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new ApiKeyAuthenticationFilter(apiKeyService), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(DevicePrincipal.AUTHORITY))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    /** Chain 2: everything else (people with JWT tokens). */
    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults()) // uses the corsConfigurationSource bean below
                .csrf(AbstractHttpConfigurer::disable) // REST API with tokens, no browser cookies
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Browsers send an OPTIONS "preflight" before cross-site calls; it carries no token.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/public/**", "/actuator/health", "/error").permitAll()
                        // WebSocket handshake; the socket itself requires a token in its first message
                        .requestMatchers("/ws").permitAll()
                        // API docs (they are disabled entirely in production, see application.properties)
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/admin/**").hasAuthority(UserType.ADMIN.authority())
                        .requestMatchers("/api/partner/**").hasAuthority(UserType.PARTNER_USER.authority())
                        .requestMatchers("/api/customer/**").hasAuthority(UserType.CUSTOMER.authority())
                        .anyRequest().denyAll()
                )
                // Read "Authorization: Bearer <jwt>" on every request and verify it
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * CORS: which OTHER websites may call this API from a browser (e.g. the admin dashboard on another port/domain).
     * Exact origins only, never "*". Tokens travel in the Authorization header, not cookies, so no credentials mode.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties cors) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(cors.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Accept-Language"));
        config.setAllowCredentials(false);
        config.setMaxAge(Duration.ofHours(1)); // browsers may cache the preflight answer for 1 hour
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /** Hashes passwords. We only ever store the hash, never the real password. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** The secret key that signs our tokens (HMAC-SHA256). */
    @Bean
    public SecretKey jwtSecretKey() {
        byte[] bytes = Base64.getDecoder().decode(jwtProperties.secret());
        if (bytes.length < 32) {
            throw new IllegalStateException("vornx.jwt.secret must be at least 32 bytes (Base64)");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    /** Creates tokens at login. */
    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    /** Checks tokens on each request: signature, expiry and issuer. */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwtProperties.issuer()));
        return decoder;
    }

    /** Turns the token's "authorities" claim into Spring authorities (TYPE_ADMIN, ROLE_SUPER_ADMIN...). */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenService.CLAIM_AUTHORITIES);
        authorities.setAuthorityPrefix(""); // already prefixed in the token

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
