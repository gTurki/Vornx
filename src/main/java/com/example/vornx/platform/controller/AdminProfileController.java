package com.example.vornx.platform.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.vornx.platform.dto.AdminUserResponse;
import com.example.vornx.platform.service.AdminUserService;
import com.example.vornx.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Profile", description = "The logged-in admin")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminProfileController {

    private final AdminUserService adminUserService;

    /** GET /api/admin/me -> the logged-in admin (taken from the token, never from the URL). */
    @GetMapping("/me")
    public AdminUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return adminUserService.getProfile(AuthUser.from(jwt).id());
    }
}
