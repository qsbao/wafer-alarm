package com.waferalarm.trend;

import java.util.List;

public record TrendChartResponse(List<TrendPoint> points, boolean downsampled) {}
