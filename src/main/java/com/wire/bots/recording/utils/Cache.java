package com.wire.bots.recording.utils;

import com.wire.xenon.WireClient;
import com.wire.xenon.backend.models.User;
import com.wire.xenon.exceptions.HttpException;
import com.wire.xenon.models.RemoteMessage;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Cache {
    private static final ConcurrentHashMap<String, File> assetsMap = new ConcurrentHashMap<>();//<assetKey, File>
    private static final ConcurrentHashMap<UUID, User> users = new ConcurrentHashMap<>();//<userId, User>
    private final WireClient client;

    public Cache(WireClient client) {
        this.client = client;
    }

    public static void clear(UUID userId) {
        users.remove(userId);
    }

    File getAssetFile(RemoteMessage message) {
        return assetsMap.computeIfAbsent(message.getAssetId(), k -> {
            try {
                byte[] image = downloadAsset(message);
                return Helper.saveAsset(image, message);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    protected byte[] downloadAsset(RemoteMessage message) throws Exception {
        return client.downloadAsset(message.getAssetId(),
                message.getAssetToken(),
                message.getSha256(),
                message.getOtrKey());
    }

    protected User getUserInternal(UUID userId) throws HttpException {
        return client.getUser(userId);
    }

    public User getUser(UUID userId) {
        return users.computeIfAbsent(userId, k -> {
            try {
                return getUserInternal(userId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
