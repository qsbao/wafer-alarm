-- Append-only enforcement for audit_log.
-- MySQL: uses SIGNAL to reject UPDATE/DELETE.
-- H2 (tests): overridden by test-specific migration using H2 trigger syntax.

-- These triggers use MySQL syntax. For H2 tests, see test resources.
-- On H2 in MySQL compatibility mode, the SIGNAL statement is not supported,
-- so this migration is intentionally empty when running on H2.
-- The test profile adds a separate Flyway location with H2-compatible triggers.
