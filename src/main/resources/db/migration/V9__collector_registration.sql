CREATE TABLE collector_registration (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    collector_id VARCHAR(255) NOT NULL UNIQUE,
    owned_source_system_ids TEXT NOT NULL,
    registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_heartbeat_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
