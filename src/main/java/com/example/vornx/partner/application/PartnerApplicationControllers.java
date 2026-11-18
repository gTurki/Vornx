package com.example.vornx.partner.application;

import com.example.vornx.audit.Audited;
import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.partner.application.PartnerApplicationService.ApplicationRequest;
import com.example.vornx.partner.application.PartnerApplicationService.ApplicationResponse;
import com.example.vornx.partner.application.PartnerApplicationService.ApprovalResponse;
import com.example.vornx.security.AuthUser;
import com.example.vornx.security.session.ClientInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

public final class PartnerApplicationControllers {

    private PartnerApplicationControllers() {
    }

    @Tag(name = "Public - Website")
    @SecurityRequirements
    @RestController
    @RequestMapping("/api/public/partner-applications")
    @RequiredArgsConstructor
    public static class Public {

        private final PartnerApplicationService service;

        @Operation(summary = "\"انضم كشريك\": a company applies to become a partner",
                description = "409 ALREADY_PARTNER / APPLICATION_PENDING; 429 too many.")
        @PostMapping
        @ResponseStatus(HttpStatus.ACCEPTED)
        public Map<String, Boolean> submit(@Valid @RequestBody ApplicationRequest req, HttpServletRequest http) {
            ClientInfo client = ClientInfo.from(http);
            service.submit(req, client.ipAddress(), client.userAgent());
            return Map.of("success", true);
        }
    }

    @Tag(name = "Admin - Partner applications")
    @RestController
    @RequestMapping("/api/admin/partner-applications")
    @RequiredArgsConstructor
    public static class Admin {

        private final PartnerApplicationService service;

        /** accountManagerId: optional, default = the admin who approves. */
        public record ApproveRequest(Long accountManagerId) {
        }

        public record RejectRequest(@NotBlank @Size(max = 500) String reason) {
        }

        @GetMapping
        public PageResponse<ApplicationResponse> list(@RequestParam(required = false) PartnerApplication.Status status,
                                                      @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                                      Pageable pageable) {
            return service.list(status, pageable);
        }

        @GetMapping("/pending-count")
        public Map<String, Long> pendingCount() {
            return Map.of("count", service.countPending());
        }

        @Operation(summary = "Approve: creates the partner and emails its manager an invite")
        @PostMapping("/{id}/approve")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNT_MANAGER')")
        @Audited(action = "PARTNER_APPLICATION_APPROVED", target = "PARTNER_APPLICATION", targetId = "#id")
        public ApprovalResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                        @RequestBody(required = false) ApproveRequest req) {
            return service.approve(id, AuthUser.from(jwt).id(), req != null ? req.accountManagerId() : null);
        }

        @Operation(summary = "Reject with a reason (emailed to the applicant)")
        @PostMapping("/{id}/reject")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN','ACCOUNT_MANAGER')")
        @Audited(action = "PARTNER_APPLICATION_REJECTED", target = "PARTNER_APPLICATION", targetId = "#id")
        public ApplicationResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                          @Valid @RequestBody RejectRequest req) {
            return service.reject(id, AuthUser.from(jwt).id(), req.reason());
        }
    }
}
