package com.example.vornx.platform.overview;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Overview", description = "The dashboard home")
@RestController
@RequestMapping("/api/admin/overview")
@RequiredArgsConstructor
public class AdminOverviewController {

    private final AdminOverviewService overview;

    @Operation(summary = "Everything on the dashboard home: sectors, KPIs, 6-month revenue, needs attention, top partners")
    @GetMapping
    public AdminOverviewResponse overview() {
        return overview.overview();
    }
}
