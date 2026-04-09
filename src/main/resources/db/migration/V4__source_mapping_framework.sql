-- V4: Source mapping framework - source systems, mappings, and connector run logs

CREATE TABLE source_system (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    db_name VARCHAR(255),
    credentials_ref VARCHAR(255),
    network_zone VARCHAR(100),
    timezone VARCHAR(50) NOT NULL DEFAULT 'UTC',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE source_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_system_id BIGINT NOT NULL,
    parameter_id BIGINT NOT NULL,
    query_template TEXT NOT NULL,
    value_column VARCHAR(255) NOT NULL,
    watermark_column VARCHAR(255) NOT NULL,
    context_column_mapping TEXT,
    poll_interval_seconds INT NOT NULL DEFAULT 300,
    row_cap INT NOT NULL DEFAULT 10000,
    query_timeout_seconds INT NOT NULL DEFAULT 30,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mapping_source_system FOREIGN KEY (source_system_id) REFERENCES source_system(id),
    CONSTRAINT fk_mapping_parameter FOREIGN KEY (parameter_id) REFERENCES parameter(id)
);

CREATE TABLE connector_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_mapping_id BIGINT NOT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP NOT NULL,
    rows_pulled INT NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_run_mapping FOREIGN KEY (source_mapping_id) REFERENCES source_mapping(id)
);

CREATE INDEX idx_connector_run_mapping ON connector_run(source_mapping_id);
CREATE INDEX idx_connector_run_started ON connector_run(started_at);
