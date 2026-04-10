package com.waferalarm.trend;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrendChartResponse(
        List<TrendPoint> points,
        boolean downsampled,
        Double upperLimit,
        Double lowerLimit,
        List<AlarmBand> alarmBands
) {
    /** Backwards-compatible constructor for tests that don't need limits/bands */
    public TrendChartResponse(List<TrendPoint> points, boolean downsampled) {
        this(points, downsampled, null, null, null);
    }
}
