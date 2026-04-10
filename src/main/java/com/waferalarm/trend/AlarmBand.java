package com.waferalarm.trend;

import java.time.Instant;

public record AlarmBand(Instant from, Instant to, String severity) {}
