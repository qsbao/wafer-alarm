-- V14: Unmapped-data inbox tables

CREATE TABLE staging_unmapped (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_system_id BIGINT NOT NULL,
    column_key VARCHAR(255) NOT NULL,
    sample_value VARCHAR(1000),
    occurrence_count INT NOT NULL DEFAULT 1,
    first_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_unmapped_source_system FOREIGN KEY (source_system_id) REFERENCES source_system(id),
    CONSTRAINT uq_unmapped_source_column UNIQUE (source_system_id, column_key)
);

CREATE TABLE staging_dismissed (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_system_id BIGINT NOT NULL,
    column_key VARCHAR(255) NOT NULL,
    dismissed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dismissed_source_system FOREIGN KEY (source_system_id) REFERENCES source_system(id),
    CONSTRAINT uq_dismissed_source_column UNIQUE (source_system_id, column_key)
);

CREATE INDEX idx_unmapped_source ON staging_unmapped(source_system_id);
CREATE INDEX idx_dismissed_source ON staging_dismissed(source_system_id);
