package com.waferalarm.trend;

import java.time.Instant;

public record TrendPoint(Instant ts, double value) {}
