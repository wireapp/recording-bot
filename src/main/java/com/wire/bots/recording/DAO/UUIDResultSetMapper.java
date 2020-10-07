package com.wire.bots.recording.DAO;

import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class UUIDResultSetMapper implements ResultSetMapper<UUID> {
    @Override
    public UUID map(int i, ResultSet rs, StatementContext statementContext) throws SQLException {
        Object uuid = rs.getObject("uuid");
        if (uuid != null)
            return (UUID) uuid;
        return null;
    }
}
