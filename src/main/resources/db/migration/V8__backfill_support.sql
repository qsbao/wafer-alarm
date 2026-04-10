-- V8: Backfill support - backfill fields on source_mapping + backfill_task tracking table

ALTER TABLE source_mapping ADD COLUMN backfill_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE source_mapping ADD COLUMN backfill_window_days INT NOT NULL DEFAULT 30;

CREATE TABLE backfill_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_mapping_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    backfill_from TIMESTAMP NOT NULL,
    backfill_to TIMESTAMP NOT NULL,
    rows_processed INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    error TEXT,
    last_processed_ts TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_backfill_mapping FOREIGN KEY (source_mapping_id) REFERENCES source_mapping(id)
);

CREATE INDEX idx_backfill_task_mapping ON backfill_task(source_mapping_id);
CREATE INDEX idx_backfill_task_status ON backfill_task(status);

ALTER TABLE measurement ADD COLUMN backfilled BOOLEAN NOT NULL DEFAULT FALSE;
