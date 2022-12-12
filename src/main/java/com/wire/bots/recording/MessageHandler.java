package com.wire.bots.recording;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waz.model.Messages;
import com.wire.bots.recording.DAO.ChannelsDAO;
import com.wire.bots.recording.DAO.EventsDAO;
import com.wire.bots.recording.model.Config;
import com.wire.bots.recording.model.Event;
import com.wire.bots.recording.model.Log;
import com.wire.bots.recording.utils.Cache;
import com.wire.xenon.MessageHandlerBase;
import com.wire.xenon.WireClient;
import com.wire.xenon.assets.MessageDelete;
import com.wire.xenon.assets.MessageText;
import com.wire.xenon.backend.models.Conversation;
import com.wire.xenon.backend.models.Member;
import com.wire.xenon.backend.models.NewBot;
import com.wire.xenon.backend.models.SystemMessage;
import com.wire.xenon.exceptions.HttpException;
import com.wire.xenon.factories.StorageFactory;
import com.wire.xenon.models.*;
import com.wire.xenon.tools.Logger;
import org.jdbi.v3.core.Jdbi;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.wire.bots.recording.CommandManager.HELP;
import static com.wire.bots.recording.CommandManager.WELCOME_LABEL;
import static com.wire.bots.recording.utils.Helper.date;
import static com.wire.bots.recording.utils.Helper.getConversationPath;

public class MessageHandler extends MessageHandlerBase {
    private final ObjectMapper mapper = new ObjectMapper();

    private final Config config;

    private final EventsDAO eventsDAO;

    private final StorageFactory storageFactory;

    private final CommandManager commandManager;
    private final ChannelsDAO channelsDAO;

    MessageHandler(Jdbi jdbi, StorageFactory storageFactory, CommandManager commandManager) {
        config = Service.instance.getConfig();
        eventsDAO = jdbi.onDemand(EventsDAO.class);
        channelsDAO = jdbi.onDemand(ChannelsDAO.class);

        this.storageFactory = storageFactory;

        this.commandManager = commandManager;
    }

    @Override
    public boolean onNewBot(NewBot newBot, String serviceToken) {
        // Assure there are no other bots in this conversation. If yes then refuse to join
        for (Member member : newBot.conversation.members) {
            if (member.service != null) {
                Logger.warning("Rejecting NewBot. Provider: %s service: %s",
                        member.service.providerId,
                        member.service.id);
                return false; // we don't want to be in a conv if other bots are there.
            }
        }
        return true;
    }

    @Override
    public void onNewConversation(WireClient client, SystemMessage msg) {
        try {
            client.send(new MessageText(WELCOME_LABEL));
            client.send(new MessageText(HELP), msg.from);

            UUID convId = msg.conversation.id;
            UUID botId = client.getId();
            UUID messageId = msg.id;
            String type = msg.type;

            persist(convId, null, botId, messageId, type, msg);

            generateHtml(client, botId, convId);
        } catch (Exception e) {
            Logger.error("onNewConversation: %s %s", client.getId(), e);
        }
    }

    @Override
    public void onConversationDelete(UUID botId, SystemMessage msg) {
        UUID conversationId = msg.conversation.id;

        // clear the history for this conv if the conversation was deleted by the bot owner
        try {
            NewBot state = storageFactory.create(botId).getState();
            if (Objects.equals(state.origin.id, msg.from)) {
                commandManager.onPrivate(conversationId);

                int clear = eventsDAO.clear(conversationId);
                Logger.warning("onConversationDelete: %s deleted %d messages", conversationId, clear);
            }
        } catch (Exception e) {
            Logger.exception(e, "onConversationDelete: %s", botId);
        }
    }

    @Override
    public void onConversationRename(WireClient client, SystemMessage msg) {
        UUID botId = client.getId();
        UUID convId = msg.conversation.id;
        UUID messageId = msg.id;
        String type = msg.type;

        persist(convId, null, botId, messageId, type, msg);

        generateHtml(client, botId, convId);
    }

    @Override
    public void onBotRemoved(UUID botId, SystemMessage msg) {
        UUID convId = msg.conversation.id;
        UUID messageId = msg.id;
        String type = "conversation.member-leave.bot-removed";

        persist(convId, null, botId, messageId, type, msg);

        commandManager.onPrivate(convId);
    }

    @Override
    public void onMemberJoin(WireClient client, SystemMessage msg) {
        UUID botId = client.getId();
        UUID convId = msg.conversation.id;
        UUID messageId = msg.id;
        String type = msg.type;

        persist(convId, null, botId, messageId, type, msg);

        generateHtml(client, botId, convId);
    }

    @Override
    public void onMemberLeave(WireClient client, SystemMessage msg) {
        UUID botId = client.getId();
        UUID convId = msg.conversation.id;
        UUID messageId = msg.id;
        String type = msg.type;

        persist(convId, null, botId, messageId, type, msg);

        generateHtml(client, botId, convId);
    }

    @Override
    public void onText(WireClient client, TextMessage msg) {
        UUID botId = client.getId();
        UUID userId = msg.getUserId();
        UUID messageId = msg.getMessageId();
        UUID convId = msg.getConversationId();
        String type = "conversation.otr-message-add.new-text";

        try {
            if (commandManager.command(client, msg)) {
                return;
            }

            persist(convId, userId, botId, messageId, type, msg);

            if (config.kibana) {
                kibana(type, msg, client);
            }
        } catch (Exception e) {
            Logger.exception(e, "OnText: %s", client.getId());
        }
    }

    @Override
    public void onText(WireClient client, EphemeralTextMessage msg) {
        onText(client, (TextMessage) msg);
    }

    @Override
    public void onEditText(WireClient client, EditedTextMessage msg) {
        UUID botId = client.getId();
        UUID userId = msg.getUserId();
        UUID messageId = msg.getMessageId();
        UUID convId = msg.getConversationId();
        String type = "conversation.otr-message-add.edit-text";

        try {
            persist(convId, userId, botId, messageId, type, msg);

            if (config.edit) {
                UUID replacingMessageId = msg.getReplacingMessageId();
                eventsDAO.update(replacingMessageId, type, msg.getText());
            }

            if (config.kibana) {
                kibana(type, msg, client);
            }
        } catch (Exception e) {
            Logger.exception(e, "onEditText: %s", client.getId());
        }
    }

    @Override
    public void onButtonClick(WireClient client, ButtonActionMessage msg) {
        super.onButtonClick(client, msg);

        try {
            NewBot state = storageFactory.create(client.getId()).getState();
            UUID owner = state.origin.id;

            if (Objects.equals(owner, msg.getUserId()) && Objects.equals(msg.getButtonId(), "yes")) {
                int records = eventsDAO.clear(msg.getConversationId());
                client.send(new MessageText(String.format("Deleted %d messages", records)), msg.getUserId());
                commandManager.onPdf(client, msg.getUserId(), msg.getConversationId());
            }

            // Delete the Dialog message
            client.send(new MessageDelete(msg.getReference()));
        } catch (Exception e) {
            Logger.exception(e, "onButtonClick: %s", client.getId());
        }
    }

    @Override
    public void onDelete(WireClient client, DeletedTextMessage msg) {
        UUID botId = client.getId();
        UUID messageId = msg.getMessageId();
        UUID convId = msg.getConversationId();
        UUID userId = msg.getUserId();
        String type = "conversation.otr-message-add.delete-text";

        persist(convId, userId, botId, messageId, type, msg);

        if (config.delete) {
            eventsDAO.delete(msg.getDeletedMessageId());
        }
    }

    @Override
    public void onAssetData(WireClient client, RemoteMessage msg) {
        UUID botId = client.getId();
        UUID convId = msg.getConversationId();
        UUID messageId = msg.getMessageId();
        UUID userId = msg.getUserId();
        String type = "conversation.otr-message-add.asset-data";

        try {
            persist(convId, userId, botId, messageId, type, msg);
        } catch (Exception e) {
            Logger.error("onAssetData: %s %s %s", botId, messageId, e);
        }
    }

    @Override
    public void onFilePreview(WireClient client, FilePreviewMessage msg) {
        UUID botId = client.getId();
        UUID convId = msg.getConversationId();
        UUID messageId = UUID.randomUUID();
        UUID userId = msg.getUserId();
        String type = "conversation.otr-message-add.file-preview";

        try {
            persist(convId, userId, botId, messageId, type, msg);
        } catch (Exception e) {
            Logger.exception(e, "onFilePreview: %s %s", botId, messageId);
        }
    }

    @Override
    public void onVideoPreview(WireClient client, VideoPreviewMessage msg) {
        UUID botId = client.getId();
        UUID convId = msg.getConversationId();
        UUID messageId = UUID.randomUUID();
        UUID userId = msg.getUserId();
        String type = "conversation.otr-message-add.video-preview";

        try {
            persist(convId, userId, botId, messageId, type, msg);
        } catch (Exception e) {
            Logger.error("onVideoPreview: %s %s %s", botId, messageId, e);
        }
    }

    @Override
    public void onAudioPreview(WireClient client, AudioPreviewMessage msg) {
        UUID botId = client.getId();
        UUID convId = msg.getConversationId();
        UUID messageId = UUID.randomUUID();
        UUID userId = msg.getUserId();
        String type = "conversation.otr-message-add.audio-preview";

        try {
            persist(convId, userId, botId, messageId, type, msg);
        } catch (Exception e) {
            Logger.error("onAudioPreview: %s %s %s", botId, messageId, e);
        }
    }

    @Override
    public void onPhotoPreview(WireClient client, PhotoPreviewMessage msg) {
        UUID botId = client.getId();
        UUID convId = msg.getConversationId();
        UUID messageId = UUID.randomUUID();
        UUID userId = msg.getUserId();
        String type = "conversation.otr-message-add.image-preview";

        try {
            persist(convId, userId, botId, messageId, type, msg);
        } catch (Exception e) {
            Logger.exception(e, "onPhotoPreview: %s %s", botId, messageId);
        }
    }

    @Override
    public void onLinkPreview(WireClient client, LinkPreviewMessage msg) {
        UUID botId = client.getId();
        UUID convId = client.getConversationId();
        UUID messageId = msg.getMessageId();
        UUID userId = msg.getUserId();
        String type = "conversation.otr-message-add.link-preview";

        try {
            persist(convId, userId, botId, messageId, type, msg);
        } catch (Exception e) {
            Logger.error("onLinkPreview: %s %s %s", botId, messageId, e);
        }
    }

    @Override
    public void onReaction(WireClient client, ReactionMessage msg) {
        UUID botId = client.getId();
        UUID convId = msg.getConversationId();
        UUID messageId = msg.getMessageId();
        UUID userId = msg.getUserId();
        String type = "conversation.otr-message-add.new-reaction";

        persist(convId, userId, botId, messageId, type, msg);
    }

    @Override
    public void onPing(WireClient client, PingMessage msg) {
        UUID botId = client.getId();
        UUID convId = msg.getConversationId();
        UUID messageId = msg.getMessageId();
        UUID userId = msg.getUserId();
        String type = "conversation.otr-message-add.new-ping";

        persist(convId, userId, botId, messageId, type, msg);
    }

    @Override
    public void onUserUpdate(UUID id, UUID userId) {
        Logger.info("onUserUpdate: %s, userId: %s", id, userId);
        Cache.removeUser(userId);
    }

    @Override
    public void onEvent(WireClient client, UUID userId, Messages.GenericMessage event) {
        UUID botId = client.getId();
        UUID convId = client.getConversationId();

        Logger.info("onEvent: bot: %s, conv: %s, from: %s", botId, convId, userId);

        generateHtml(client, botId, convId);
    }

    private void generateHtml(WireClient client, UUID botId, UUID convId) {
        try {
            if (null != channelsDAO.contains(convId)) {
                List<Event> events = eventsDAO.listAllAsc(convId);
                String filename = getConversationPath(convId, config.salt);

                File file = EventProcessor.saveHtml(client, events, filename);
                assert file.exists();
            }
        } catch (Exception e) {
            Logger.error("generateHtml: %s %s", botId, e);
        }
    }

    private void persist(UUID convId, UUID senderId, UUID userId, UUID msgId, String type, Object msg)
            throws RuntimeException {
        try {
            String payload = mapper.writeValueAsString(msg);
            int insert = eventsDAO.insert(msgId, convId, type, payload);

            Logger.info("%s: conv: %s, %s -> %s, msg: %s, insert: %d",
                    type,
                    convId,
                    senderId,
                    userId,
                    msgId,
                    insert);
        } catch (Exception e) {
            String error = String.format("%s: conv: %s, user: %s, msg: %s, e: %s",
                    type,
                    convId,
                    userId,
                    msgId,
                    e);
            Logger.error(error);
            throw new RuntimeException(error);
        }
    }

    void kibana(String type, TextMessage msg, WireClient client) throws IOException, HttpException, ParseException {
        Log.Kibana kibana = new Log.Kibana();
        kibana.id = msg.getEventId();
        kibana.type = type;
        kibana.messageID = msg.getMessageId();
        kibana.conversationID = msg.getConversationId();
        kibana.from = msg.getUserId();
        kibana.sent = date(msg.getTime());
        kibana.text = msg.getText();

        kibana.sender = client.getUser(msg.getUserId()).handle;

        Conversation conversation = client.getConversation();
        kibana.conversationName = conversation.name;

        for (Member m : conversation.members) {
            kibana.participants.add(client.getUser(m.id).handle);
        }

        Log log = new Log();
        log.securehold = kibana;
        System.out.println(mapper.writeValueAsString(log));
    }
}
