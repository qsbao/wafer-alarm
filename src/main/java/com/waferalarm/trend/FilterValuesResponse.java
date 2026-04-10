package com.waferalarm.trend;

import java.util.List;

public record FilterValuesResponse(
        List<String> tools,
        List<String> recipes,
        List<String> products,
        List<String> lots
) {}
