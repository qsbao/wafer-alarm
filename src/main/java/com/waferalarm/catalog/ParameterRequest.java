package com.waferalarm.catalog;

public record ParameterRequest(
        String name,
        String unit,
        String description,
        String area,
        Double defaultLowerLimit,
        Double defaultUpperLimit
) {}
