package com.waferalarm.trend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/trend-chart")
public class TrendChartController {

    private final TrendChartService trendChartService;

    public TrendChartController(TrendChartService trendChartService) {
        this.trendChartService = trendChartService;
    }

    @GetMapping
    public TrendChartResponse getTrendChart(
            @RequestParam Long parameterId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String tool,
            @RequestParam(required = false) String recipe,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String lot) {
        return trendChartService.query(parameterId, from, to, tool, recipe, product, lot);
    }
}
