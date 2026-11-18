package com.example.vornx.platform.customers;

import com.example.vornx.audit.Audited;
import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.common.enums.AccountStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin dashboard -> "العملاء". Every admin can look; only SUPER_ADMIN and SUPPORT_AGENT can suspend or reactivate.
 * (Issuing a card stays at POST /api/admin/customers/{vornxId}/cards.)
 */
@Tag(name = "Admin - Customers & cards", description = "Look up customers by VORNX ID and issue cards")
@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
public class AdminCustomersController {

    private final AdminCustomerService service;

    public record StatusRequest(@NotNull AccountStatus status) {
    }

    @Operation(summary = "Search customers", description = "q = name, email, mobile or VORNX ID. status = ACTIVE / SUSPENDED.")
    @GetMapping
    public PageResponse<AdminCustomerService.CustomerRow> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AccountStatus status,
            @ParameterObject @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(q, status, pageable);
    }

    @Operation(summary = "Everything about one customer: card, wallet, keys, subscriptions, tickets, recent doors")
    @GetMapping("/{vornxId}/details")
    public AdminCustomerService.CustomerDetails details(@PathVariable String vornxId) {
        return service.details(vornxId);
    }

    @PatchMapping("/{vornxId}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT_AGENT')")
    @Audited(action = "CUSTOMER_STATUS_CHANGED", target = "CUSTOMER", details = "#vornxId + ' -> ' + #req.status()")
    public AdminCustomerService.CustomerRow setStatus(@PathVariable String vornxId, @Valid @RequestBody StatusRequest req) {
        return service.setStatus(vornxId, req.status());
    }
}
