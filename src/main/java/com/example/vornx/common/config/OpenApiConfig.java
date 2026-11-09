package com.example.vornx.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: http://localhost:8080/swagger-ui.html
 *
 * How to try a protected endpoint:
 *   1. Group "1. Public" -> POST /api/public/auth/.../login -> "Try it out" -> copy "accessToken"
 *   2. Click "Authorize" (top right) -> paste it into bearerAuth -> Authorize
 *   3. Every request now sends "Authorization: Bearer <token>"
 * Device endpoints use the apiKey scheme instead (header X-API-Key).
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";
    public static final String API_KEY = "apiKey";

    @Bean
    public OpenAPI vornxOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("VORNX API")
                        .version("v1")
                        .description("""
                                One card, connected access.

                                **How to authenticate**
                                1. Call a login endpoint in the **Public** group and copy `accessToken`.
                                2. Click **Authorize** and paste it into **bearerAuth**.
                                3. Readers/locks (Devices group) use **apiKey** instead: create one with \
                                `POST /api/partner/api-keys` as a PARTNER_ADMIN.

                                Errors follow RFC 9457 (`status`, `title`, `detail`, and `code` for business rules).
                                """))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token from a /api/public/auth/... login"))
                        .addSecuritySchemes(API_KEY, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("Reader key, e.g. vx_...")))
                // Default for every endpoint; public and device controllers override it.
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    // One group per audience -> a dropdown at the top right of Swagger UI.

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder().group("1. Public").pathsToMatch("/api/public/**").build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder().group("2. Admin dashboard").pathsToMatch("/api/admin/**").build();
    }

    @Bean
    public GroupedOpenApi partnerApi() {
        return GroupedOpenApi.builder().group("3. Partner portal").pathsToMatch("/api/partner/**").build();
    }

    @Bean
    public GroupedOpenApi customerApi() {
        return GroupedOpenApi.builder().group("4. Customer app").pathsToMatch("/api/customer/**").build();
    }

    @Bean
    public GroupedOpenApi deviceApi() {
        return GroupedOpenApi.builder().group("5. Devices (readers)").pathsToMatch("/api/devices/**").build();
    }
}
