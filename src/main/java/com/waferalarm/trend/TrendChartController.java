package com.waferalarm.trend;

import com.waferalarm.domain.MeasurementRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trend-chart")
public class TrendChartController {

    private final TrendChartService trendChartService;
    private final MeasurementRepository measurementRepo;

    public TrendChartController(TrendChartService trendChartService,
                                MeasurementRepository measurementRepo) {
        this.trendChartService = trendChartService;
        this.measurementRepo = measurementRepo;
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

    @GetMapping("/multi")
    public ResponseEntity<?> getMultiTrendChart(
            @RequestParam List<Long> parameterIds,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String tool,
            @RequestParam(required = false) String recipe,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String lot) {
        if (parameterIds.size() > 5) {
            return ResponseEntity.badRequest().body("Maximum 5 parameters allowed");
        }
        Map<Long, TrendChartResponse> result = new LinkedHashMap<>();
        for (Long pid : parameterIds) {
            result.put(pid, trendChartService.query(pid, from, to, tool, recipe, product, lot));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/filters")
    public FilterValuesResponse getFilterValues() {
        return new FilterValuesResponse(
                measurementRepo.findDistinctTools(),
                measurementRepo.findDistinctRecipes(),
                measurementRepo.findDistinctProducts(),
                measurementRepo.findDistinctLots()
        );
    }
}
