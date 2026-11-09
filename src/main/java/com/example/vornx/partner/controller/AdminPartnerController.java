package com.example.vornx.partner.controller;

import com.example.vornx.audit.Audited;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.partner.dto.InvitePartnerUserRequest;
import com.example.vornx.partner.dto.PartnerRequest;
import com.example.vornx.partner.dto.PartnerResponse;
import com.example.vornx.partner.dto.PartnerStatusRequest;
import com.example.vornx.partner.dto.PartnerUserInviteResponse;
import com.example.vornx.partner.dto.PartnerUserResponse;
import com.example.vornx.partner.enums.PartnerStatus;
import com.example.vornx.partner.enums.Sector;
import com.example.vornx.partner.service.PartnerService;
import com.example.vornx.partner.service.PartnerUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin dashboard -> "الجهات" (partners).
 * All admins can read. Only SUPER_ADMIN and ACCOUNT_MANAGER can change things.
 */
@Tag(name = "Admin - Partners", description = "Create, search, activate/suspend partners and invite their first staff")
@RestController
@RequestMapping("/api/admin/partners")
@RequiredArgsConstructor
public class AdminPartnerController {

    private static final String CAN_MANAGE = "hasAnyRole('SUPER_ADMIN','ACCOUNT_MANAGER')";

    private final PartnerService partnerService;
    private final PartnerUserService partnerUserService;

    /** GET /api/admin/partners?sector=HOTEL&status=ACTIVE&q=arab&page=0&size=20&sort=name,asc */
    @GetMapping
    public PageResponse<PartnerResponse> search(@RequestParam(required = false) Sector sector,
                                                @RequestParam(required = false) PartnerStatus status,
                                                @RequestParam(required = false) String q,
                                                @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
                                                Pageable pageable) {
        return partnerService.search(sector, status, q, pageable);
    }

    @GetMapping("/{id}")
    public PartnerResponse get(@PathVariable Long id) {
        return partnerService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_MANAGE)
    @Audited(action = "PARTNER_CREATED", target = "PARTNER", details = "#req.name()")
    public PartnerResponse create(@Valid @RequestBody PartnerRequest req) {
        return partnerService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    @Audited(action = "PARTNER_UPDATED", target = "PARTNER")
    public PartnerResponse update(@PathVariable Long id, @Valid @RequestBody PartnerRequest req) {
        return partnerService.update(id, req);
    }

    /** Activate after onboarding, or suspend (blocks all their staff logins). */
    @PatchMapping("/{id}/status")
    @PreAuthorize(CAN_MANAGE)
    @Audited(action = "PARTNER_STATUS_CHANGED", target = "PARTNER", details = "#req.status()")
    public PartnerResponse changeStatus(@PathVariable Long id, @Valid @RequestBody PartnerStatusRequest req) {
        return partnerService.changeStatus(id, req.status());
    }

    @GetMapping("/{id}/users")
    public List<PartnerUserResponse> listUsers(@PathVariable Long id) {
        partnerService.getEntity(id); // 404 if the partner doesn't exist
        return partnerUserService.listTeam(id);
    }

    /** Invite the partner's first staff member (usually a PARTNER_ADMIN). */
    @PostMapping("/{id}/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_MANAGE)
    @Audited(action = "PARTNER_USER_INVITED", target = "PARTNER_USER", targetId = "#result.user().id()", partnerId = "#id", details = "#req.email() + ' as ' + #req.role()")
    public PartnerUserInviteResponse inviteUser(@PathVariable Long id, @Valid @RequestBody InvitePartnerUserRequest req) {
        return partnerUserService.invite(id, req);
    }
}
