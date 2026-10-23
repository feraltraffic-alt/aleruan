package com.aleruan.dealos.analytics.api;

import com.aleruan.dealos.analytics.application.AnalyticsService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
class AnalyticsController {

    private final AnalyticsService service;

    AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    /** Margin cuts; dates filter by the day the deal was created (Prague time), both ends included. */
    @GetMapping("/margin")
    AnalyticsService.Margin margin(@RequestParam(defaultValue = "CONTRACTED") AnalyticsService.Scope scope,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @RequestParam(required = false) String direction,
                                   @RequestParam(required = false) String country,
                                   @RequestParam(defaultValue = "CZK") String currency) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("The period starts after it ends");
        }
        if (!currency.matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("Currency is a three-letter code");
        }
        return service.margin(new AnalyticsService.Filter(scope, from, to, blank(direction), blank(country), currency));
    }

    /** Running sites, flagged counterparties and incomplete evidence packages. */
    @GetMapping("/health")
    AnalyticsService.Health health() {
        return service.health();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
