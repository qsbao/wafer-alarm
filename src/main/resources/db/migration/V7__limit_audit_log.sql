CREATE TABLE limit_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    limit_id BIGINT NOT NULL,
    parameter_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    context_match_json VARCHAR(1000),
    upper_limit DOUBLE,
    lower_limit DOUBLE,
    actor VARCHAR(255) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_limit_audit_limit ON limit_audit_log(limit_id);
