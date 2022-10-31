package com.wire.bots.recording.utils;

import com.wire.bots.recording.Service;
import com.wire.xenon.WireClient;
import com.wire.xenon.backend.models.User;
import com.wire.xenon.exceptions.HttpException;
import com.wire.xenon.models.RemoteMessage;

import java.io.File;
import java.security.NoSuchAlgorithmException;
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

    File getAssetFile(RemoteMessage message) throws NoSuchAlgorithmException {
        String salt = Service.instance.getConfig().salt;
        String key = Helper.key(message.getAssetId(), salt);
        return assetsMap.computeIfAbsent(key, k -> {
            try {
                byte[] image = client.downloadAsset(message.getAssetId(),
                        message.getAssetToken(),
                        message.getSha256(),
                        message.getOtrKey());
                return Helper.saveAsset(image, key);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public File getProfileFile(String assetId) {
        return assetsMap.computeIfAbsent(assetId, k -> {
            try {
                byte[] image = client.downloadProfilePicture(assetId);
                return Helper.saveProfileAsset(image, k);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
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
