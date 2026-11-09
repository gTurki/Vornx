package com.example.vornx.customer.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.vornx.customer.dto.CustomerResponse;
import com.example.vornx.customer.service.CustomerService;
import com.example.vornx.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Customer - Profile", description = "The logged-in customer")
@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerProfileController {

    private final CustomerService customerService;

    /** GET /api/customer/me -> the logged-in customer ("أهلًا يا فهد", points, tier...). */
    @GetMapping("/me")
    public CustomerResponse me(@AuthenticationPrincipal Jwt jwt) {
        return customerService.getProfile(AuthUser.from(jwt).id());
    }
}
