package com.waferalarm.trend;

import org.springframework.format.annotation.DateTimeFormat;
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
            @RequestParam Instant to) {
        return trendChartService.query(parameterId, from, to);
    }
}
