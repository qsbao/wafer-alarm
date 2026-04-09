-- V2: Add alarm lifecycle columns for auto-close and suppression

ALTER TABLE alarm ADD COLUMN consecutive_clean_count INT NOT NULL DEFAULT 0;
ALTER TABLE alarm ADD COLUMN suppressed_until TIMESTAMP NULL;
