package com.example.vornx.access.overview;

import com.example.vornx.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Partner - Overview", description = "The portal's numbers: 7-day entries, arrivals/departures, per-site stats")
@RestController
@RequestMapping("/api/partner/overview")
@RequiredArgsConstructor
public class PartnerOverviewController {

    private final PartnerOverviewService overview;

    @Operation(summary = "Plan, account manager, entries per day (7 days), today's arrivals/departures, per-site stats")
    @GetMapping
    public PartnerOverviewResponse overview(@AuthenticationPrincipal Jwt jwt) {
        return overview.overview(AuthUser.from(jwt).partnerId());
    }
}
