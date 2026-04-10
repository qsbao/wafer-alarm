-- V5: Rule versioning, parameter limits, and alarm rule_version_id

-- Parameter limits: separate from rule definitions
CREATE TABLE parameter_limit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parameter_id BIGINT NOT NULL,
    context_match_json VARCHAR(1000) NOT NULL DEFAULT '{}',
    upper_limit DOUBLE,
    lower_limit DOUBLE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_param_limit_parameter FOREIGN KEY (parameter_id) REFERENCES parameter(id)
);

CREATE INDEX idx_param_limit_parameter ON parameter_limit(parameter_id);

-- Rule version: every edit creates a new version row
CREATE TABLE rule_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    rule_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL,
    author VARCHAR(255) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rule_version_rule FOREIGN KEY (rule_id) REFERENCES rule(id)
);

CREATE INDEX idx_rule_version_rule ON rule_version(rule_id);

-- Add current_version_id to rule table
ALTER TABLE rule ADD COLUMN current_version_id BIGINT;

-- Add rule_version_id to alarm table
ALTER TABLE alarm ADD COLUMN rule_version_id BIGINT;
