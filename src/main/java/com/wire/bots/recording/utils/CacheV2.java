package com.wire.bots.recording.utils;

import com.wire.bots.sdk.exceptions.HttpException;
import com.wire.bots.sdk.models.MessageAssetBase;
import com.wire.bots.sdk.server.model.User;
import com.wire.bots.sdk.tools.Logger;
import com.wire.bots.sdk.user.API;

import javax.naming.AuthenticationException;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CacheV2 {
    private static final ConcurrentHashMap<String, File> pictures = new ConcurrentHashMap<>();//<assetKey, Picture>
    private static final ConcurrentHashMap<UUID, User> users = new ConcurrentHashMap<>();//<userId, User>
    private static final ConcurrentHashMap<UUID, File> profiles = new ConcurrentHashMap<>();//<userId, Picture>
    private API api;

    public CacheV2(API api) {
        this.api = api;
    }

    public CacheV2() {
        try {
            this.api = HelperV2.getApi();
        } catch (HttpException | AuthenticationException e) {
            e.printStackTrace();
        }
    }

    File getAssetFile(MessageAssetBase message) {
        File file = pictures.computeIfAbsent(message.getAssetKey(), k -> {
            try {
                return HelperV2.downloadAsset(api, message);
            } catch (Exception e) {
                Logger.warning("Cache.getAssetFile: %s", e);
                api = getApi();
                return null;
            }
        });

        if (file == null)
            file = HelperV2.assetFile(message.getAssetKey(), message.getMimeType());
        return file;
    }

    private API getApi() {
        try {
            return HelperV2.getApi();
        } catch (HttpException | AuthenticationException e) {
            e.printStackTrace();
            return null;
        }
    }

    File getProfileImage(User user) {
        File file = profiles.computeIfAbsent(user.id, k -> {
            try {
                return HelperV2.getProfile(api, user);
            } catch (Exception e) {
                Logger.warning("Cache.getProfileImage: userId: %s, ex: %s", user.id, e);
                api = getApi();
                return null;
            }
        });

        if (file == null)
            file = new File(HelperV2.avatarFile(user.id));
        return file;
    }

    public User getUser(UUID userId) {
        User user = users.computeIfAbsent(userId, k -> {
            try {
                return api.getUser(userId);
            } catch (Exception e) {
                Logger.warning("Cache.getUser: userId: %s, ex: %s", userId, e);
                api = getApi();
                return null;
            }
        });

        if (user == null) {
            user = new User();
            user.id = userId;
            user.name = userId.toString();
        }
        return user;
    }
}
