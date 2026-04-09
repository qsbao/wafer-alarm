package com.waferalarm.collector;

public record SourceSystemRequest(
        String name,
        String host,
        Integer port,
        String dbName,
        String credentialsRef,
        String networkZone,
        String timezone
) {}
