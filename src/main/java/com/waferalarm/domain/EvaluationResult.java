package com.waferalarm.domain;

import java.util.List;
import java.util.Map;

public record EvaluationResult(
        List<AlarmEvent> events,
        Map<String, RuleStateData> updatedState
) {}
