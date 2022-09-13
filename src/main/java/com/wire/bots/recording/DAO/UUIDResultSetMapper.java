package com.wire.bots.recording.DAO;

import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class UUIDResultSetMapper implements ColumnMapper<UUID> {
    @Override
    public UUID map(ResultSet rs, int columnNumber, StatementContext ctx) throws SQLException {
        Object uuid = rs.getObject("uuid");
        if (uuid != null)
            return (UUID) uuid;
        return null;
    }
}
