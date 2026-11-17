package com.example.vornx.security;

import com.example.vornx.customer.repository.CustomerRepository;
import com.example.vornx.customer.service.PhoneVerificationPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * With mobile verification on, a customer whose number isn't confirmed can only use: their profile, the
 * mobile check itself, sessions/logout, password and security settings. Everything else in /api/customer
 * answers 403 PHONE_NOT_VERIFIED. Enforced HERE, not only by hiding pages in the app.
 */
@Configuration
@RequiredArgsConstructor
public class PhoneVerifiedInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private final PhoneVerificationPolicy policy;
    private final CustomerRepository customers;

    /** Thrown to the global handler -> 403 with code PHONE_NOT_VERIFIED. */
    public static class PhoneNotVerifiedException extends RuntimeException {
        public PhoneNotVerifiedException() {
            super("Confirm your mobile number first");
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this)
                .addPathPatterns("/api/customer/**")
                .excludePathPatterns("/api/customer/me", "/api/customer/me/**",
                        "/api/customer/phone-verification", "/api/customer/phone-verification/**",
                        "/api/customer/sessions", "/api/customer/sessions/**",
                        "/api/customer/security/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!policy.isEnabled() || "OPTIONS".equals(request.getMethod())) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return true; // Spring Security already refused anonymous calls
        }
        AuthUser user = AuthUser.from(jwt);
        if (user.type() == UserType.CUSTOMER && !customers.existsByIdAndPhoneVerifiedAtIsNotNull(user.id())) {
            throw new PhoneNotVerifiedException();
        }
        return true;
    }
}
