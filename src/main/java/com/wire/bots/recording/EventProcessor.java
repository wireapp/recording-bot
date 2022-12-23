package com.wire.bots.recording;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wire.bots.recording.model.Event;
import com.wire.bots.recording.utils.Cache;
import com.wire.bots.recording.utils.Collector;
import com.wire.bots.recording.utils.Helper;
import com.wire.xenon.WireClient;
import com.wire.xenon.backend.models.Member;
import com.wire.xenon.backend.models.SystemMessage;
import com.wire.xenon.backend.models.User;
import com.wire.xenon.models.*;
import com.wire.xenon.tools.Logger;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

class EventProcessor {
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final HashMap<UUID, Collector> collectors = new HashMap<>();

    public static File saveHtml(Collector collector, List<Event> events) throws IOException {
        for (Event event : events) {
            add(collector, event);
        }
        String htmlFilename = Helper.getHtmlFilename(collector.getChannelName());
        return collector.executeFile(htmlFilename);
    }

    public static File saveHtml(UUID convId, List<Event> events) throws IOException {
        Collector collector = collectors.get(convId);
        for (Event event : events) {
            add(collector, event);
        }
        String htmlFilename = Helper.getHtmlFilename(collector.getChannelName());
        return collector.executeFile(htmlFilename);
    }

    public static void add(UUID convId, Event event) {
        Collector collector = collectors.get(convId);
        add(collector, event);
    }

    public static void register(WireClient client, String channelName) {
        UUID conversationId = client.getConversationId();
        Collector collector = new Collector(conversationId, channelName, new Cache(client));
        collectors.put(conversationId, collector);
    }

    public static void saveHtml(UUID convId) {
        try {
            Collector collector = collectors.get(convId);
            String htmlFilename = Helper.getHtmlFilename(collector.getChannelName());
            collector.executeFile(htmlFilename);
        } catch (IOException e) {
            Logger.exception(e, "saveHtml: %s", convId);
        }
    }

    private static void add(Collector collector, Event event) {
        try {
            switch (event.type) {
                case "conversation.create": {
                    SystemMessage msg = mapper.readValue(event.payload, SystemMessage.class);
                    collector.setConvName(msg.conversation.name);
                    collector.setConversationId(msg.conversation.id);

                    String text = formatConversation(msg, collector.getCache());
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
                case "conversation.otr-message-add.edit-text": {
                    EditedTextMessage message = mapper.readValue(event.payload, EditedTextMessage.class);
                    message.setText(message.getText());
                    message.setQuotedMessageId(message.getReplacingMessageId());
                    collector.addEdit(message);
                }
                break;
                case "conversation.otr-message-add.link-preview": {
                    LinkPreviewMessage message = mapper.readValue(event.payload, LinkPreviewMessage.class);
                    collector.addLink(message);
                }
                break;
                case "conversation.otr-message-add.asset-data": {
                    RemoteMessage message = mapper.readValue(event.payload, RemoteMessage.class);
                    collector.add(message);
                }
                break;
                case "conversation.otr-message-add.file-preview": {
                    FilePreviewMessage message = mapper.readValue(event.payload, FilePreviewMessage.class);
                    collector.add(message);
                }
                break;
                case "conversation.otr-message-add.image-preview": {
                    PhotoPreviewMessage message = mapper.readValue(event.payload, PhotoPreviewMessage.class);
                    collector.add(message);
                }
                break;
                case "conversation.otr-message-add.video-preview": {
                    VideoPreviewMessage message = mapper.readValue(event.payload, VideoPreviewMessage.class);
                    collector.add(message);
                }
                break;
                case "conversation.otr-message-add.audio-preview": {
                    AudioPreviewMessage message = mapper.readValue(event.payload, AudioPreviewMessage.class);
                    collector.add(message);
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

    private static String formatConversation(SystemMessage msg, Cache cache) {
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
