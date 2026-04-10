package com.waferalarm.health;

import java.util.List;

public record HealthReport(
        List<ConnectorHealth> connectors,
        EvaluatorHealth evaluator
) {}
