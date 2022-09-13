package com.wire.bots.recording;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wire.bots.recording.model.Event;
import com.wire.bots.recording.utils.Cache;
import com.wire.bots.recording.utils.Collector;
import com.wire.xenon.WireClient;
import com.wire.xenon.backend.models.Member;
import com.wire.xenon.backend.models.SystemMessage;
import com.wire.xenon.backend.models.User;
import com.wire.xenon.models.*;
import com.wire.xenon.tools.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

class EventProcessor {
    private final ObjectMapper mapper = new ObjectMapper();

    EventProcessor() {
    }

    void clearCache(UUID userId) {
        Cache.clear(userId);
    }

    File saveHtml(WireClient client, List<Event> events, String filename, boolean withPreviews) throws IOException {
        Collector collector = new Collector(new Cache(client));
        for (Event event : events) {
            add(client, collector, event, withPreviews);
        }
        return collector.executeFile(filename);
    }

    private void add(WireClient client, Collector collector, Event event, boolean withPreviews) {
        try {
            switch (event.type) {
                case "conversation.create": {
                    SystemMessage msg = mapper.readValue(event.payload, SystemMessage.class);
                    collector.setConvName(msg.conversation.name);
                    collector.setConversationId(msg.convId);

                    String text = formatConversation(msg, collector.getCache(), client);
                    collector.addSystem(text, msg.time, event.type, msg.id);
                }
                break;
                case "conversation.rename": {
                    SystemMessage msg = mapper.readValue(event.payload, SystemMessage.class);
                    String convName = msg.conversation.name;
                    collector.setConvName(convName);

                    String text = String.format("**%s** %s **%s**",
                            collector.getUserName(msg.from),
                            "renamed conversation",
                            convName);
                    collector.addSystem(text, msg.time, event.type, msg.id);
                }
                break;
                case "conversation.otr-message-add.new-text": {
                    TextMessage message = mapper.readValue(event.payload, TextMessage.class);
                    collector.add(message);
                }
                break;
                case "conversation.otr-message-add.asset-data": {
                    RemoteMessage message = mapper.readValue(event.payload, RemoteMessage.class);
                    collector.add(message);
                }
                break;
                case "conversation.otr-message-add.file-preview": {
                    FilePreviewMessage message = mapper.readValue(event.payload, FilePreviewMessage.class);
                    //collector.add(message);
                }
                break;
                case "conversation.otr-message-add.image-preview": {
                    PhotoPreviewMessage message = mapper.readValue(event.payload, PhotoPreviewMessage.class);
                    //collector.add(message);
                }
                break;
                case "conversation.otr-message-add.video-preview": {
                    VideoMessage message = mapper.readValue(event.payload, VideoMessage.class);
                    //collector.add(message);
                }
                break;
                case "conversation.member-join": {
                    SystemMessage msg = mapper.readValue(event.payload, SystemMessage.class);
                    for (UUID userId : msg.users) {
                        String format = String.format("**%s** %s **%s**",
                                collector.getUserName(msg.from),
                                "added",
                                collector.getUserName(userId));
                        collector.addSystem(format, msg.time, event.type, msg.id);
                    }
                }
                break;
                case "conversation.member-leave": {
                    SystemMessage msg = mapper.readValue(event.payload, SystemMessage.class);
                    for (UUID userId : msg.users) {
                        String format = String.format("**%s** %s **%s**",
                                collector.getUserName(msg.from),
                                "removed",
                                collector.getUserName(userId));
                        collector.addSystem(format, msg.time, event.type, msg.id);
                    }
                }
                break;
                case "conversation.member-leave.bot-removed": {
                    SystemMessage msg = mapper.readValue(event.payload, SystemMessage.class);
                    String format = String.format("**%s** %s",
                            collector.getUserName(msg.from),
                            "stopped recording");
                    collector.addSystem(format, msg.time, event.type, msg.id);
                }
                break;
                case "conversation.otr-message-add.edit-text": {
                    EditedTextMessage message = mapper.readValue(event.payload, EditedTextMessage.class);
                    message.setText(message.getText());
                    collector.addEdit(message);
                }
                break;
                case "conversation.otr-message-add.delete-text": {
                    DeletedTextMessage msg = mapper.readValue(event.payload, DeletedTextMessage.class);
                    String userName = collector.getUserName(msg.getUserId());
                    String text = String.format("**%s** deleted something", userName);
                    collector.addSystem(text, msg.getTime(), event.type, msg.getMessageId());
                }
                break;
                case "conversation.otr-message-add.new-reaction": {
                    ReactionMessage message = mapper.readValue(event.payload, ReactionMessage.class);
                    collector.add(message);
                }
                break;
                case "conversation.otr-message-add.new-ping": {
                    PingMessage msg = mapper.readValue(event.payload, PingMessage.class);
                    String userName = collector.getUserName(msg.getUserId());
                    String text = String.format("**%s** pinged", userName);
                    collector.addSystem(text, msg.getTime(), event.type, msg.getMessageId());
                }
                break;
            }
        } catch (Exception e) {
            Logger.exception(e, "EventProcessor.add: msg: %s `%s`", event.messageId, event.type);
        }
    }

    class Asset {
        RemoteMessage remote;
        OriginMessage preview;
    }

    private String formatConversation(SystemMessage msg, Cache cache, WireClient client) {
        StringBuilder sb = new StringBuilder();
        User user = cache.getUser(msg.from);
        sb.append(String.format("**%s** started recording in **%s** with: \n",
                user.name,
                msg.conversation.name));
        for (Member member : msg.conversation.members) {
            user = cache.getUser(member.id);
            sb.append(String.format("- **%s** \n", user.name));
        }
        return sb.toString();
    }
}
