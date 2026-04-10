package com.waferalarm.collector;

import com.waferalarm.domain.MeasurementEntity;

import java.util.List;
import java.util.Map;

public record PullResult(
        List<MeasurementEntity> measurements,
        Map<String, String> unmappedColumns
) {}
