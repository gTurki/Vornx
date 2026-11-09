package com.example.vornx.access.controller;

import com.example.vornx.audit.Audited;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.vornx.access.dto.AccessPointRequest;
import com.example.vornx.access.dto.AccessPointResponse;
import com.example.vornx.access.dto.AccessPointStatusRequest;
import com.example.vornx.access.dto.SiteRequest;
import com.example.vornx.access.dto.SiteResponse;
import com.example.vornx.access.service.AccessPointService;
import com.example.vornx.access.service.SiteService;
import com.example.vornx.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Partner portal -> "الفنادق التابعة" (sites) and their rooms/doors/gates.
 * Everyone on the team can read. PARTNER_ADMIN and MANAGER can create/edit.
 * FRONT_DESK can also change a room's status (occupied, cleaning...).
 */
@Tag(name = "Partner - Sites & access points", description = "Hotels, buildings, rooms, doors and gates")
@RestController
@RequestMapping("/api/partner")
@RequiredArgsConstructor
public class PartnerSiteController {

    private static final String CAN_EDIT = "hasAnyRole('PARTNER_ADMIN','MANAGER')";
    private static final String CAN_OPERATE = "hasAnyRole('PARTNER_ADMIN','MANAGER','FRONT_DESK')";

    private final SiteService siteService;
    private final AccessPointService accessPointService;

    // ---------- Sites ----------

    @GetMapping("/sites")
    public List<SiteResponse> listSites(@AuthenticationPrincipal Jwt jwt) {
        return siteService.list(partnerId(jwt));
    }

    @GetMapping("/sites/{siteId}")
    public SiteResponse getSite(@AuthenticationPrincipal Jwt jwt, @PathVariable Long siteId) {
        return siteService.get(partnerId(jwt), siteId);
    }

    @PostMapping("/sites")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_EDIT)
    @Audited(action = "SITE_CREATED", target = "SITE", details = "#req.name()")
    public SiteResponse createSite(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SiteRequest req) {
        return siteService.create(partnerId(jwt), req);
    }

    @PutMapping("/sites/{siteId}")
    @PreAuthorize(CAN_EDIT)
    @Audited(action = "SITE_UPDATED", target = "SITE")
    public SiteResponse updateSite(@AuthenticationPrincipal Jwt jwt, @PathVariable Long siteId,
                                   @Valid @RequestBody SiteRequest req) {
        return siteService.update(partnerId(jwt), siteId, req);
    }

    // ---------- Access points ----------

    @GetMapping("/sites/{siteId}/access-points")
    public List<AccessPointResponse> listAccessPoints(@AuthenticationPrincipal Jwt jwt, @PathVariable Long siteId) {
        return accessPointService.list(partnerId(jwt), siteId);
    }

    @PostMapping("/sites/{siteId}/access-points")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_EDIT)
    @Audited(action = "ACCESS_POINT_CREATED", target = "ACCESS_POINT", details = "#result.code()")
    public AccessPointResponse createAccessPoint(@AuthenticationPrincipal Jwt jwt, @PathVariable Long siteId,
                                                 @Valid @RequestBody AccessPointRequest req) {
        return accessPointService.create(partnerId(jwt), siteId, req);
    }

    @PutMapping("/access-points/{accessPointId}")
    @PreAuthorize(CAN_EDIT)
    @Audited(action = "ACCESS_POINT_UPDATED", target = "ACCESS_POINT")
    public AccessPointResponse updateAccessPoint(@AuthenticationPrincipal Jwt jwt, @PathVariable Long accessPointId,
                                                 @Valid @RequestBody AccessPointRequest req) {
        return accessPointService.update(partnerId(jwt), accessPointId, req);
    }

    @PatchMapping("/access-points/{accessPointId}/status")
    @PreAuthorize(CAN_OPERATE)
    @Audited(action = "ACCESS_POINT_STATUS_CHANGED", target = "ACCESS_POINT", details = "#result.code() + ' -> ' + #req.status()")
    public AccessPointResponse changeAccessPointStatus(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable Long accessPointId,
                                                       @Valid @RequestBody AccessPointStatusRequest req) {
        return accessPointService.changeStatus(partnerId(jwt), accessPointId, req.status());
    }

    private static Long partnerId(Jwt jwt) {
        return AuthUser.from(jwt).partnerId();
    }
}
