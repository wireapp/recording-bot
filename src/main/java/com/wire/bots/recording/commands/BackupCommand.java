package com.wire.bots.recording.commands;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.wire.bots.recording.utils.Collector;
import com.wire.bots.recording.utils.InstantCache;
import com.wire.bots.recording.utils.PdfGenerator;
import com.wire.bots.sdk.models.EditedTextMessage;
import com.wire.bots.sdk.models.ImageMessage;
import com.wire.bots.sdk.models.TextMessage;
import io.dropwizard.cli.Command;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.client.ssl.TlsConfiguration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.glassfish.jersey.media.multipart.MultiPartFeature;

import javax.ws.rs.client.Client;
import java.io.File;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class BackupCommand extends Command {
    private final HashMap<UUID, _Conversation> conversationHashMap = new HashMap<>();
    private final HashMap<UUID, Collector> collectorHashMap = new HashMap<>();

    public BackupCommand() {
        super("pdf", "Convert Wire Desktop backup file into PDF");
    }

    @Override
    public void configure(Subparser subparser) {

        subparser.addArgument("-in", "--input")
                .dest("in")
                .type(String.class)
                .required(true)
                .help("Backup file");

        subparser.addArgument("-e", "--email")
                .dest("email")
                .type(String.class)
                .required(true)
                .help("Email address");

        subparser.addArgument("-p", "--password")
                .dest("password")
                .type(String.class)
                .required(true)
                .help("Password");
    }

    public static void unzip(String source, String destination) throws ZipException {
        ZipFile zipFile = new ZipFile(source);
        zipFile.extractAll(destination);
    }

    @Override
    public void run(Bootstrap<?> bootstrap, Namespace namespace) throws Exception {
        final String email = namespace.getString("email");
        final String password = namespace.getString("password");
        final String in = namespace.getString("in");
        //unzip(in, "recording/in");
        final File eventsFile = new File("recording/in/events.json");
        final File conversationsFile = new File("recording/in/conversations.json");

        final ObjectMapper objectMapper = bootstrap.getObjectMapper();
        final Event[] events = objectMapper.readValue(eventsFile, Event[].class);
        final _Conversation[] conversations = objectMapper.readValue(conversationsFile, _Conversation[].class);

        final Environment environment = new Environment(getName(),
                objectMapper,
                bootstrap.getValidatorFactory().getValidator(),
                bootstrap.getMetricRegistry(),
                bootstrap.getClassLoader());

        JerseyClientConfiguration jerseyCfg = new JerseyClientConfiguration();
        jerseyCfg.setChunkedEncodingEnabled(false);
        jerseyCfg.setGzipEnabled(false);
        jerseyCfg.setGzipEnabledForRequests(false);
        jerseyCfg.setTimeout(Duration.seconds(20));
        final TlsConfiguration tlsConfiguration = new TlsConfiguration();
        tlsConfiguration.setProtocol("TLSv1.2");
        jerseyCfg.setTlsConfiguration(tlsConfiguration);

        final Client client = new JerseyClientBuilder(environment)
                .using(jerseyCfg)
                .withProvider(MultiPartFeature.class)
                .withProvider(JacksonJsonProvider.class)
                .build(getName());

        InstantCache cache = new InstantCache(email, password, client);

        for (_Conversation conversation : conversations) {
            if (conversation.name == null || conversation.name.isEmpty()) {
                if (conversation.others != null && !conversation.others.isEmpty())
                    conversation.name = cache.getUser(conversation.others.get(0)).name;
            }
            conversationHashMap.put(conversation.id, conversation);
        }

        for (Event event : events) {
            Collector collector = getCollector(event.conversation, cache);
            switch (event.type) {
                case "conversation.group-creation": {
                    onGroupCreation(collector, event);
                }
                break;
                case "conversation.message-add": {
                    onMessageAdd(collector, event);
                }
                break;
                case "conversation.asset-add": {
                    onAssetAdd(collector, event);
                }
                break;
                case "conversation.member-join": {
                    onMemberJoin(collector, event);
                }
                break;
            }
        }

        for (Collector collector : collectorHashMap.values()) {
            final String html = collector.execute();
            String out = String.format("recording/output/%s.pdf", collector.getConvName());
            PdfGenerator.save(out, html, "file:/Users/dejankovacevic/Projects/recording-bot");
        }
    }

    private Collector getCollector(UUID convId, InstantCache cache) {
        return collectorHashMap.computeIfAbsent(convId, x -> {
            Collector collector = new Collector(cache);
            _Conversation conversation = getConversation(convId);
            collector.setConvName(conversation.name);
            return collector;
        });
    }

    private _Conversation getConversation(UUID convId) {
        return conversationHashMap.get(convId);
    }

    private void onMemberJoin(Collector collector, Event event) throws ParseException {
        for (UUID userId : event.data.user_ids) {
            String txt = String.format("**%s** %s **%s**",
                    collector.getUserName(event.from),
                    "added",
                    collector.getUserName(userId));
            collector.addSystem(txt, event.time, "conversation.member-join", event.id);
        }
    }

    private void onAssetAdd(Collector collector, Event event) throws ParseException {
        if (event.data.otrKey == null || event.data.sha256 == null)
            return;
        final ImageMessage img = new ImageMessage(event.id, event.conversation, null, event.from);
        img.setTime(event.time);
        img.setSize(event.data.contentLength);
        img.setMimeType(event.data.contentType);
        img.setAssetToken(event.data.token);
        img.setAssetKey(event.data.key);
        img.setOtrKey(toArray(event.data.otrKey));
        img.setSha256(toArray(event.data.sha256));
        collector.add(img);
    }

    private void onMessageAdd(Collector collector, Event event) throws ParseException {
        if (event.data.replacingMessageId != null) {
            EditedTextMessage edit = new EditedTextMessage(event.id, event.conversation, null, event.from);
            edit.setText(event.data.content);
            edit.setTime(event.editedTime);
            edit.setReplacingMessageId(event.data.replacingMessageId);
            collector.addEdit(edit);
        } else {
            final TextMessage txt = new TextMessage(event.id, event.conversation, null, event.from);
            txt.setTime(event.time);
            txt.setText(event.data.content);
            if (event.data.quote != null) {
                txt.setQuotedMessageId(event.data.quote.messageId);
            }
            collector.add(txt);
        }

//        if (event.reactions != null) {
//            for (UUID userId : event.reactions.keySet()) {
//                ReactionMessage like = new ReactionMessage(UUID.randomUUID(), event.conversation, null, userId);
//                like.setReactionMessageId(event.id);
//                like.setEmoji(event.reactions.get(userId));
//                like.setTime(event.time);
//                collector.add(like);
//            }
//        }
    }

    private byte[] toArray(HashMap<String, Byte> otrKey) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(32);
        for (String key : otrKey.keySet()) {
            byteBuffer.put(Integer.parseInt(key), otrKey.get(key));
        }
        return byteBuffer.array();
    }

    private void onGroupCreation(Collector collector, Event event) throws ParseException {
        String txt = String.format("**%s** created conversation **%s** with:",
                collector.getUserName(event.from),
                event.data.name);
        collector.addSystem(txt, event.time, "conversation.create", event.id);
        for (UUID userId : event.data.userIds) {
            final String userName = collector.getUserName(userId);
            collector.addSystem(userName, event.time, "conversation.member-join", UUID.randomUUID());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Event {
        @JsonProperty
        UUID conversation;
        @JsonProperty
        UUID from;
        @JsonProperty
        UUID id;
        @JsonProperty
        String type;
        @JsonProperty
        String time;
        @JsonProperty
        Data data;
        @JsonProperty("edited_time")
        String editedTime;
        //@JsonProperty("reactions")
        //HashMap<UUID, String> reactions;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Data {
        @JsonProperty
        String content;
        @JsonProperty
        ArrayList<UUID> user_ids;
        @JsonProperty
        ArrayList<UUID> userIds;
        @JsonProperty
        String name;
        @JsonProperty("content_length")
        int contentLength;
        @JsonProperty("content_type")
        String contentType;
        @JsonProperty("key")
        String key;
        @JsonProperty("token")
        String token;
        @JsonProperty("otr_key")
        HashMap<String, Byte> otrKey;
        @JsonProperty("sha256")
        HashMap<String, Byte> sha256;
        @JsonProperty("replacing_message_id")
        UUID replacingMessageId;
        @JsonProperty("quote")
        Quote quote;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Quote {
        @JsonProperty("message_id")
        UUID messageId;
        @JsonProperty("user_id")
        UUID userId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class _Conversation {
        @JsonProperty
        UUID id;
        @JsonProperty
        String name;
        @JsonProperty
        ArrayList<UUID> others;
    }
}
