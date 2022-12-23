package com.wire.bots.recording.DAO;

import org.jdbi.v3.sqlobject.config.RegisterColumnMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.UUID;

public interface ChannelsDAO {
    @SqlUpdate("INSERT INTO Channels (conversationId, botId, name, time) " +
            "VALUES (:conversationId, :botId, :name, CURRENT_TIMESTAMP) ON CONFLICT (conversationId) DO " +
            "UPDATE SET name = EXCLUDED.name")
    int insert(@Bind("conversationId") UUID conversationId,
               @Bind("botId") UUID botId,
               @Bind("name") String name);

    @SqlQuery("SELECT url FROM Channels WHERE conversationId = :conversationId")
    String getName(@Bind("conversationId") UUID conversationId);

    @SqlQuery("SELECT botId AS UUID FROM Channels WHERE conversationId = :conversationId")
    @RegisterColumnMapper(UUIDResultSetMapper.class)
    UUID getBotId(@Bind("conversationId") UUID conversationId);

    @SqlQuery("SELECT conversationId AS UUID FROM Channels")
    @RegisterColumnMapper(UUIDResultSetMapper.class)
    List<UUID> listConversations();

    @SqlUpdate("DELETE FROM Channels WHERE conversationId = :conversationId")
    int delete(@Bind("conversationId") UUID conversationId);
}
