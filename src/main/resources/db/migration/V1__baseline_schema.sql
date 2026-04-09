-- V1: Walking skeleton - minimal tables for end-to-end slice

CREATE TABLE parameter (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    unit VARCHAR(50),
    description VARCHAR(500),
    default_upper_limit DOUBLE,
    default_lower_limit DOUBLE
);

CREATE TABLE measurement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parameter_id BIGINT NOT NULL,
    wafer_id VARCHAR(255) NOT NULL,
    measured_value DOUBLE NOT NULL,
    ts TIMESTAMP NOT NULL,
    ingested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tool VARCHAR(255),
    recipe VARCHAR(255),
    product VARCHAR(255),
    lot_id VARCHAR(255),
    context_json TEXT,
    CONSTRAINT fk_measurement_parameter FOREIGN KEY (parameter_id) REFERENCES parameter(id)
);

CREATE INDEX idx_measurement_ts ON measurement(ts);
CREATE INDEX idx_measurement_ingested_at ON measurement(ingested_at);
CREATE INDEX idx_measurement_parameter_ts ON measurement(parameter_id, ts);
CREATE UNIQUE INDEX idx_measurement_wafer_param ON measurement(wafer_id, parameter_id);

CREATE TABLE rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parameter_id BIGINT NOT NULL,
    rule_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'WARNING',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rule_parameter FOREIGN KEY (parameter_id) REFERENCES parameter(id)
);

CREATE TABLE alarm (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    parameter_id BIGINT NOT NULL,
    context_key VARCHAR(500),
    state VARCHAR(20) NOT NULL DEFAULT 'firing',
    severity VARCHAR(20) NOT NULL,
    occurrence_count INT NOT NULL DEFAULT 1,
    first_violation_at TIMESTAMP NOT NULL,
    last_violation_at TIMESTAMP NOT NULL,
    last_value DOUBLE,
    threshold_value DOUBLE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_alarm_rule FOREIGN KEY (rule_id) REFERENCES rule(id),
    CONSTRAINT fk_alarm_parameter FOREIGN KEY (parameter_id) REFERENCES parameter(id)
);

CREATE INDEX idx_alarm_state ON alarm(state);
CREATE INDEX idx_alarm_rule_context ON alarm(rule_id, context_key);

CREATE TABLE eval_watermark (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    watermark_key VARCHAR(255) NOT NULL UNIQUE,
    last_ingested_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE collector_watermark (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_key VARCHAR(255) NOT NULL UNIQUE,
    last_ts TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
