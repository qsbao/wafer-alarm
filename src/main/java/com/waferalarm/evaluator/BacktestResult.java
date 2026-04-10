package com.waferalarm.evaluator;

import java.util.List;
import java.util.Map;

public record BacktestResult(
        int totalViolations,
        Map<String, Long> severityBreakdown,
        List<BacktestViolation> violations
) {}
