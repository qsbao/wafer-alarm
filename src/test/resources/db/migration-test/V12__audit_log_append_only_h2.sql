-- H2-specific append-only triggers for audit_log
CREATE TRIGGER audit_log_no_update BEFORE UPDATE ON audit_log
FOR EACH ROW CALL "com.waferalarm.test.AuditLogAppendOnlyTrigger";

CREATE TRIGGER audit_log_no_delete BEFORE DELETE ON audit_log
FOR EACH ROW CALL "com.waferalarm.test.AuditLogAppendOnlyTrigger";
