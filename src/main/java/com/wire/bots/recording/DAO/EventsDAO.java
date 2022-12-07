package com.wire.bots.recording.DAO;

import com.wire.bots.recording.model.Event;
import org.jdbi.v3.sqlobject.config.RegisterColumnMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.UUID;

public interface EventsDAO {
    @SqlUpdate("INSERT INTO Events (messageId, conversationId, type, payload, time) " +
            "VALUES (:messageId, :conversationId, :type, :payload, CURRENT_TIMESTAMP) ON CONFLICT (messageId) DO " +
            "UPDATE SET type = EXCLUDED.type, payload = EXCLUDED.payload")
    int insert(@Bind("messageId") UUID messageId,
               @Bind("conversationId") UUID conversationId,
               @Bind("type") String type,
               @Bind("payload") String payload);

    @SqlQuery("SELECT * FROM Events WHERE messageId = :messageId")
    @RegisterColumnMapper(EventsResultSetMapper.class)
    Event get(@Bind("messageId") UUID messageId);

    @SqlUpdate("UPDATE Events SET payload = :payload, type = :type WHERE messageId = :messageId")
    int update(@Bind("messageId") UUID messageId, @Bind("type") String type, @Bind("payload") String payload);

    @SqlQuery("SELECT * FROM Events WHERE conversationId = :conversationId ORDER BY time ASC")
    @RegisterColumnMapper(EventsResultSetMapper.class)
    List<Event> listAllAsc(@Bind("conversationId") UUID conversationId);

    @SqlQuery("SELECT DISTINCT conversationId AS UUID FROM Events")
    @RegisterColumnMapper(UUIDResultSetMapper.class)
    List<UUID> listConversations();

    @SqlUpdate("DELETE FROM Events WHERE messageId = :messageId")
    int delete(@Bind("messageId") UUID messageId);

    @SqlUpdate("DELETE FROM Events WHERE conversationId = :conversationId")
    int clear(@Bind("conversationId") UUID conversationId);
}
