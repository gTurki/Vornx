package com.example.vornx.security.device;

import com.example.vornx.partner.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the "X-API-Key" header. If the key is valid, the request is treated as coming from
 * one of that partner's devices. If not, nothing is set and Spring Security answers 401.
 *
 * NOT a @Component on purpose: otherwise Spring Boot would also run it on every request of the app.
 * It is added only to the /api/devices/** security chain in SecurityConfig.
 */
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String rawKey = request.getHeader(HEADER);
        if (StringUtils.hasText(rawKey)) {
            apiKeyService.authenticate(rawKey.trim()).ifPresent(key -> {
                DevicePrincipal principal = new DevicePrincipal(key.getId(), key.getPartner().getId());
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, List.of(new SimpleGrantedAuthority(DevicePrincipal.AUTHORITY)));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            });
        }
        chain.doFilter(request, response);
    }
}
