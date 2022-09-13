package com.wire.bots.recording.utils;

import com.wire.bots.recording.ConversationTemplateTest;
import com.wire.xenon.backend.models.Asset;
import com.wire.xenon.backend.models.User;
import com.wire.xenon.models.MessageAssetBase;

import java.io.File;
import java.util.ArrayList;
import java.util.UUID;

public class TestCache extends Cache {

    public TestCache() {
        super(null);
    }

    @Override
    public User getProfile(UUID userId) {
        User ret = new User();
        ret.id = userId;
        ret.assets = new ArrayList<>();
        Asset asset = new Asset();
        asset.key = userId.toString();
        asset.size = "preview";
        ret.assets.add(asset);
        
        if (userId.equals(ConversationTemplateTest.dejan)) {
            ret.name = "Dejan";
            ret.accent = 7;

        } else {
            ret.name = "Lipis";
            ret.accent = 1;
        }
        return ret;
    }

    @Override
    public User getUser(UUID userId) {
        return getProfile(userId);
    }

    @Override
    File getProfileImage(String key) {
        return new File(String.format("src/test/resources/avatars/%s.png", key));
    }

    @Override
    File getAssetFile(MessageAssetBase message) {
        String extension = Helper.getExtension(message.getMimeType());
        return new File(String.format("src/test/resources/images/%s.%s",
                message.getAssetKey(),
                extension));
    }
}
