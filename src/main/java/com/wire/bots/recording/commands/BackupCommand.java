package com.wire.bots.recording.commands;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.wire.bots.recording.model.Config;
import com.wire.bots.recording.utils.Collector;
import com.wire.bots.recording.utils.InstantCache;
import com.wire.bots.recording.utils.PdfGenerator;
import com.wire.bots.sdk.models.EditedTextMessage;
import com.wire.bots.sdk.models.ImageMessage;
import com.wire.bots.sdk.models.ReactionMessage;
import com.wire.bots.sdk.models.TextMessage;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.glassfish.jersey.media.multipart.MultiPartFeature;

import javax.ws.rs.client.Client;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class BackupCommand extends ConfiguredCommand<Config> {
    public BackupCommand() {
        super("pdf", "Convert Wire Desktop backup file into PDF");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);

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

    @Override
    public void run(Bootstrap<Config> bootstrap, Namespace namespace, Config config) throws Exception {
        String email = namespace.getString("email");
        String password = namespace.getString("password");
        File file = new File(namespace.getString("in"));

        final ObjectMapper objectMapper = bootstrap.getObjectMapper();
        final Event[] events = objectMapper.readValue(file, Event[].class);

        final Environment environment = new Environment(getName(),
                objectMapper,
                bootstrap.getValidatorFactory().getValidator(),
                bootstrap.getMetricRegistry(),
                bootstrap.getClassLoader());

        JerseyClientConfiguration jerseyCfg = config.getJerseyClient();
        jerseyCfg.setChunkedEncodingEnabled(false);
        jerseyCfg.setGzipEnabled(false);
        jerseyCfg.setGzipEnabledForRequests(false);

        final Client client = new JerseyClientBuilder(environment)
                .using(jerseyCfg)
                .withProvider(MultiPartFeature.class)
                .withProvider(JacksonJsonProvider.class)
                .build(getName());

        InstantCache cache = new InstantCache(email, password, client);
        Collector collector = new Collector(cache);

        for (Event event : events) {
            switch (event.type) {
                case "conversation.message-add": {
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

                    if (event.reactions != null) {
                        for (UUID userId : event.reactions.keySet()) {
                            ReactionMessage like = new ReactionMessage(UUID.randomUUID(), event.conversation, null, userId);
                            like.setReactionMessageId(event.id);
                            like.setEmoji(event.reactions.get(userId));
                            like.setTime(event.time);
                            collector.add(like);
                        }
                    }
                }
                break;
                case "conversation.asset-add": {
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
                break;
                case "conversation.member-join": {
                    for (UUID userId : event.data.userIds) {
                        String txt = String.format("**%s** %s **%s**",
                                collector.getUserName(event.from),
                                "added",
                                collector.getUserName(userId));
                        collector.addSystem(txt, event.time, "conversation.member-join", event.id);
                    }
                }
                break;
            }
        }

        collector.setConvName("Test Conversation");
        final String html = collector.execute();
        PdfGenerator.save("Recording Test.pdf", html, "file:/Users/dejankovacevic/Projects/recording-bot");
    }

    private byte[] toArray(HashMap<String, Byte> otrKey) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(32);
        for (String key : otrKey.keySet()) {
            byteBuffer.put(Integer.parseInt(key), otrKey.get(key));
        }
        return byteBuffer.array();
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
        @JsonProperty("reactions")
        HashMap<UUID, String> reactions;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Data {
        @JsonProperty
        String content;
        @JsonProperty("user_ids")
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
}
