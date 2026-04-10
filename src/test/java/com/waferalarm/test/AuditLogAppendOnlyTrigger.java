package com.waferalarm.test;

import org.h2.api.Trigger;

import java.sql.Connection;
import java.sql.SQLException;

public class AuditLogAppendOnlyTrigger implements Trigger {

    @Override
    public void init(Connection conn, String schemaName, String triggerName,
                     String tableName, boolean before, int type) {}

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        throw new SQLException("audit_log is append-only: UPDATE and DELETE are not allowed");
    }

    @Override
    public void close() {}

    @Override
    public void remove() {}
}
