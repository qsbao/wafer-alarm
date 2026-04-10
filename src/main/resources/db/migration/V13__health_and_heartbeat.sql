-- Eval run log (mirrors connector_run for the evaluator tick)
CREATE TABLE eval_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    started_at TIMESTAMP(6) NOT NULL,
    finished_at TIMESTAMP(6) NOT NULL,
    measurements_processed INT NOT NULL DEFAULT 0,
    alarms_fired INT NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL,
    error TEXT,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE INDEX idx_eval_run_started_at ON eval_run (started_at DESC);
