package com.example.vornx.inquiry;

import com.example.vornx.audit.Audited;
import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.inquiry.InquiryService.InquiryRequest;
import com.example.vornx.inquiry.InquiryService.InquiryResponse;
import com.example.vornx.security.AuthUser;
import com.example.vornx.security.session.ClientInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

public final class InquiryControllers {

    private InquiryControllers() {
    }

    @Tag(name = "Public - Website")
    @RestController
    @RequestMapping("/api/public/inquiries")
    @RequiredArgsConstructor
    public static class Public {

        private final InquiryService service;

        @Operation(summary = "Contact form of the public website")
        @PostMapping
        @ResponseStatus(HttpStatus.ACCEPTED)
        public Map<String, Boolean> submit(@Valid @RequestBody InquiryRequest req, HttpServletRequest http) {
            ClientInfo client = ClientInfo.from(http);
            service.submit(req, client.ipAddress(), client.userAgent());
            return Map.of("success", true);
        }
    }

    @Tag(name = "Admin - Website inquiries")
    @RestController
    @RequestMapping("/api/admin/inquiries")
    @RequiredArgsConstructor
    public static class Admin {

        private final InquiryService service;

        public record StatusRequest(@NotNull Inquiry.Status status) {
        }

        @GetMapping
        public PageResponse<InquiryResponse> list(@RequestParam(required = false) Inquiry.Status status,
                                                  @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                                  Pageable pageable) {
            return service.list(status, pageable);
        }

        @GetMapping("/new-count")
        public Map<String, Long> newCount() {
            return Map.of("count", service.countNew());
        }

        @PatchMapping("/{id}")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNT_MANAGER','SUPPORT_AGENT')")
        @Audited(action = "INQUIRY_UPDATED", target = "INQUIRY", targetId = "#id", details = "#req.status()")
        public InquiryResponse setStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody StatusRequest req) {
            return service.setStatus(id, req.status(), AuthUser.from(jwt).id());
        }
    }
}
