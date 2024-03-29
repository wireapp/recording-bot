package com.wire.bots.recording;

import com.wire.bots.recording.DAO.ChannelsDAO;
import com.wire.bots.recording.DAO.EventsDAO;
import com.wire.bots.recording.model.Event;
import com.wire.bots.recording.utils.Helper;
import com.wire.lithium.ClientRepo;
import com.wire.xenon.WireClient;
import com.wire.xenon.tools.Logger;
import org.jdbi.v3.core.Jdbi;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class Startup {
    private final ChannelsDAO channelsDAO;
    private final EventsDAO eventsDAO;

    public Startup(Jdbi jdbi) {
        eventsDAO = jdbi.onDemand(EventsDAO.class);
        channelsDAO = jdbi.onDemand(ChannelsDAO.class);
    }

    public void warmup(ClientRepo repo) {
        Logger.info("Warming up...");
        List<UUID> conversations = channelsDAO.listConversations();
        for (UUID convId : conversations) {
            try {
                UUID botId = channelsDAO.getBotId(convId);
                if (botId != null) {
                    try (WireClient client = repo.getClient(botId)) {

                        String name = channelsDAO.getName(convId);
                        if (name == null) {
                            name = Helper.randomName(8);
                            channelsDAO.insert(convId, botId, name);
                        }

                        EventProcessor.register(client, name);

                        List<Event> events = eventsDAO.listAllAsc(convId);
                        File file = EventProcessor.saveHtml(convId, events);

                        Logger.debug("warmed up: %s", file.getName());
                        Thread.sleep(2 * 1000);
                    } catch (IOException e) {
                        Logger.warning("warmup: %s %s.. removing the bot", convId, e);
                        channelsDAO.delete(convId);
                    }
                }
            } catch (Exception e) {
                Logger.error("warmup: %s %s", convId, e);
            }
        }
        Logger.info("Finished Warming up %d convs", conversations.size());
    }
}
