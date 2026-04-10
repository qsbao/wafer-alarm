package com.waferalarm.collector;

public record SourceMappingRequest(
        Long sourceSystemId,
        Long parameterId,
        String queryTemplate,
        String valueColumn,
        String watermarkColumn,
        String contextColumnMapping,
        Integer pollIntervalSeconds,
        Integer rowCap,
        Integer queryTimeoutSeconds,
        Boolean backfillEnabled,
        Integer backfillWindowDays
) {}
