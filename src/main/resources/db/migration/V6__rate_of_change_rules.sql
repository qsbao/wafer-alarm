-- V6: Rate-of-change rules — rule_state table and ROC columns

-- Rule state: persists last_value per (rule, context) for ROC comparisons
CREATE TABLE rule_state (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    context_key_hash VARCHAR(255) NOT NULL,
    last_value DOUBLE NOT NULL,
    last_ts TIMESTAMP NOT NULL,
    last_wafer_id VARCHAR(255) NOT NULL,
    CONSTRAINT fk_rule_state_rule FOREIGN KEY (rule_id) REFERENCES rule(id),
    CONSTRAINT uq_rule_state UNIQUE (rule_id, context_key_hash)
);

CREATE INDEX idx_rule_state_rule ON rule_state(rule_id);

-- ROC fields on rule table
ALTER TABLE rule ADD COLUMN absolute_delta DOUBLE;
ALTER TABLE rule ADD COLUMN percentage_delta DOUBLE;
ALTER TABLE rule ADD COLUMN minimum_baseline DOUBLE;

-- ROC fields on rule_version table (versioned snapshot)
ALTER TABLE rule_version ADD COLUMN absolute_delta DOUBLE;
ALTER TABLE rule_version ADD COLUMN percentage_delta DOUBLE;
ALTER TABLE rule_version ADD COLUMN minimum_baseline DOUBLE;
