package com.wire.bots.recording.DAO;

import com.wire.bots.recording.model.Event;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;

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
    @RegisterMapper(EventsResultSetMapper.class)
    Event get(@Bind("messageId") UUID messageId);

    @SqlUpdate("UPDATE Events SET payload = :payload, type = :type WHERE messageId = :messageId")
    int update(@Bind("messageId") UUID messageId, @Bind("type") String type, @Bind("payload") String payload);

    @SqlQuery("SELECT * FROM Events WHERE conversationId = :conversationId ORDER BY time ASC")
    @RegisterMapper(EventsResultSetMapper.class)
    List<Event> listAllAsc(@Bind("conversationId") UUID conversationId);

    @SqlQuery("SELECT DISTINCT conversationId AS UUID FROM Events")
    @RegisterMapper(UUIDResultSetMapper.class)
    List<UUID> listConversations();

    @SqlUpdate("DELETE FROM Events WHERE messageId = :messageId")
    int delete(@Bind("messageId") UUID messageId);
}
