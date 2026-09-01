package com.westy.codmanager.analytics.web;

import com.westy.codmanager.analytics.service.AnalyticsService;
import com.westy.codmanager.analytics.web.AnalyticsDtos.BreakdownResponse;
import com.westy.codmanager.analytics.web.AnalyticsDtos.OverviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@Validated
@Tag(name = "Analytics")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    @Operation(summary = "Headline numbers: volume, revenue, cash outstanding, rates")
    public OverviewResponse overview(@AuthenticationPrincipal String ownerId,
                                     @RequestParam(defaultValue = "30")
                                     @Min(1) @Max(365) int days) {
        return service.overview(UUID.fromString(ownerId), days);
    }

    @GetMapping("/breakdown")
    @Operation(summary = "Performance by wilaya, by channel, by product and by day")
    public BreakdownResponse breakdown(@AuthenticationPrincipal String ownerId,
                                       @RequestParam(defaultValue = "30")
                                       @Min(1) @Max(365) int days,
                                       @RequestParam(defaultValue = "10")
                                       @Min(1) @Max(50) int products) {
        return service.breakdown(UUID.fromString(ownerId), days, products);
    }
}
