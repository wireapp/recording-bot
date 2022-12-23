package com.wire.bots.recording;

import com.wire.bots.recording.DAO.ChannelsDAO;
import com.wire.bots.recording.DAO.EventsDAO;
import com.wire.bots.recording.model.Config;
import com.wire.bots.recording.model.Event;
import com.wire.bots.recording.utils.Cache;
import com.wire.bots.recording.utils.Collector;
import com.wire.bots.recording.utils.Helper;
import com.wire.bots.recording.utils.PdfGenerator;
import com.wire.xenon.WireClient;
import com.wire.xenon.assets.FileAsset;
import com.wire.xenon.assets.FileAssetPreview;
import com.wire.xenon.assets.MessageText;
import com.wire.xenon.assets.Poll;
import com.wire.xenon.backend.models.NewBot;
import com.wire.xenon.factories.StorageFactory;
import com.wire.xenon.models.AssetKey;
import com.wire.xenon.models.TextMessage;
import com.wire.xenon.tools.Logger;
import com.wire.xenon.tools.Util;
import org.jdbi.v3.core.Jdbi;

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.wire.bots.recording.utils.Helper.*;

public class CommandManager {

    static final String WELCOME_LABEL = "This conversation has _recording_ enabled";
    static final String HELP = "Available commands:\n" +
            "`/pdf`     - receive previous messages in PDF format\n" +
            "`/public`  - publish this conversation\n" +
            "`/private` - stop publishing this conversation\n" +
            "`/clear`   - clear the history";
    public static final String HOME_DIR = "file:/opt/recording";

    private final ChannelsDAO channelsDAO;
    private final EventsDAO eventsDAO;
    private final StorageFactory storageFactory;
    private final Config config;

    public CommandManager(Jdbi jdbi, StorageFactory storageFactory) {
        eventsDAO = jdbi.onDemand(EventsDAO.class);
        channelsDAO = jdbi.onDemand(ChannelsDAO.class);
        this.storageFactory = storageFactory;
        config = Service.instance.getConfig();
    }

    public boolean command(WireClient client, TextMessage msg) throws Exception {
        UUID botId = client.getId();
        UUID userId = msg.getUserId();
        UUID convId = msg.getConversationId();

        final String cmd = msg.getText().toLowerCase().trim();

        // Only owner of the bot can run commands
        NewBot state = storageFactory.create(botId).getState();
        if (!Objects.equals(state.origin.id, userId))
            return false;

        switch (cmd) {
            case "/help": {
                client.send(new MessageText(HELP), userId);
                return true;
            }
            case "/pdf": {
                onPdf(client, userId, convId);
                return true;
            }
            case "/clear": {
                Poll poll = new Poll();
                poll.addText("Are you sure you want to delete the whole history for this group?\nThis cannot be undone!");
                poll.addButton("yes", "Yes");
                poll.addButton("no", "No");
                client.send(poll, userId);
                return true;
            }
            case "/public": {
                String name = Helper.randomName(8);

                EventProcessor.register(client, name);

                channelsDAO.insert(convId, botId, name);
                List<Event> events = eventsDAO.listAllAsc(convId);
                EventProcessor.saveHtml(convId, events);

                String text = String.format("%s/channel/%s.html", config.url, name);
                client.send(new MessageText(text), userId);
                return true;
            }
            case "/private": {
                onPrivate(convId);
                client.send(new MessageText("Stopped publishing the group"), userId);
                return true;
            }
        }
        return false;
    }

    public void onPdf(WireClient client, UUID userId, UUID convId) throws Exception {
        client.send(new MessageText("Generating PDF..."), userId);

        String channelsName = channelsDAO.getName(convId);

        List<Event> events = eventsDAO.listAllAsc(convId);

        Collector collector = new Collector(client.getConversationId(), channelsName, new Cache(client));
        File htmlFile = EventProcessor.saveHtml(collector, events);
        String html = Util.readFile(htmlFile);

        String convName = client.getConversation().name;
        if (convName == null) {
            convName = "Recording";
        }

        String pdfFilename = getPdfFilename(convName);
        File pdfFile = PdfGenerator.save(pdfFilename, html, HOME_DIR);

        // Post the Preview
        UUID messageId = UUID.randomUUID();
        String mimeType = "application/pdf";
        client.send(new FileAssetPreview(pdfFile.getName(), mimeType, pdfFile.length(), messageId), userId);

        FileAsset fileAsset = new FileAsset(pdfFile, mimeType, messageId);

        // Upload Asset
        AssetKey assetKey = client.uploadAsset(fileAsset);
        fileAsset.setAssetKey(assetKey.id);
        fileAsset.setAssetToken(assetKey.token);
        fileAsset.setDomain(assetKey.domain);

        Logger.info("Asset: key: %s, token: %s, domain: %s",
                fileAsset.getAssetKey(),
                fileAsset.getAssetToken(),
                fileAsset.getDomain());

        // Post Asset
        client.send(fileAsset, userId);

        pdfFile.delete();
        htmlFile.delete();//todo check if this impacts the channel
    }

    public void onPrivate(UUID convId) {
        try {
            String channelsName = channelsDAO.getName(convId);

            // Delete downloaded assets
            File assetDir = getAssetDir(channelsName);
            deleteDir(assetDir);

            // Delete the html file
            String filename = getHtmlFilename(channelsName);
            File htmlFile = new File(filename);
            htmlFile.delete();

            channelsDAO.delete(convId);
        } catch (NoSuchAlgorithmException e) {
            Logger.exception(e, "onPrivate: %s", convId);
        }
    }
}
