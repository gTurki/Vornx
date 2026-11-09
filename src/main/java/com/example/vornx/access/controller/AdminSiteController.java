package com.example.vornx.access.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.vornx.access.dto.AccessPointResponse;
import com.example.vornx.access.dto.SiteResponse;
import com.example.vornx.access.service.AccessPointService;
import com.example.vornx.access.service.SiteService;
import com.example.vornx.partner.service.PartnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin dashboard: read-only view of any partner's sites (here the partnerId comes from the URL). */
@Tag(name = "Admin - Partner sites", description = "Read-only view of any partner's sites and access points")
@RestController
@RequestMapping("/api/admin/partners/{partnerId}/sites")
@RequiredArgsConstructor
public class AdminSiteController {

    private final PartnerService partnerService;
    private final SiteService siteService;
    private final AccessPointService accessPointService;

    @GetMapping
    public List<SiteResponse> list(@PathVariable Long partnerId) {
        partnerService.getEntity(partnerId); // 404 if the partner doesn't exist
        return siteService.list(partnerId);
    }

    @GetMapping("/{siteId}/access-points")
    public List<AccessPointResponse> accessPoints(@PathVariable Long partnerId, @PathVariable Long siteId) {
        return accessPointService.list(partnerId, siteId);
    }
}
