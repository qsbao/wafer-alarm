package com.waferalarm.domain;

public record LimitData(
        long parameterId,
        Double upperLimit,
        Double lowerLimit
) {}
