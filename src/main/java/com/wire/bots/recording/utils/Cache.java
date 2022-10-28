package com.wire.bots.recording.utils;

import com.lambdaworks.crypto.SCryptUtil;
import com.wire.xenon.WireClient;
import com.wire.xenon.backend.models.User;
import com.wire.xenon.exceptions.HttpException;
import com.wire.xenon.models.RemoteMessage;
import com.wire.xenon.tools.Util;

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
        String key = key(message.getAssetId());
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

    private String key(String assetId) {
        return SCryptUtil.scrypt(assetId, 16384, 8, 1);
    }

    public File getProfileFile(String key) {
        return assetsMap.computeIfAbsent(key, k -> {
            try {
                byte[] image = client.downloadProfilePicture(key);
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
