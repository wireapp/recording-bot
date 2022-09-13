package com.wire.bots.recording.utils;

import com.wire.lithium.API;
import com.wire.xenon.backend.models.User;
import com.wire.xenon.exceptions.HttpException;
import com.wire.xenon.models.MessageAssetBase;
import com.wire.xenon.tools.Logger;
import com.wire.xenon.tools.Util;

import javax.ws.rs.client.Client;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;

public class InstantCache extends Cache {
    private final String email;
    private final String password;
    private final Client client;
    private API api;

    public InstantCache(String email, String password, Client client) throws HttpException {
        super(null);
        this.email = email;
        this.password = password;
        this.client = client;
        Access access = new LoginClient(client).login(email, password);
        this.api = new API(client, null, access.getToken());
    }

    public UUID getUserId(String handle) {
        try {
            return this.api.getUserId(handle);
        } catch (HttpException e) {
            Logger.error("InstantCache.getUserId: username: %s, ex: %s", handle, e);
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected User getUserObject(UUID userId) {
        try {
            return api.getUser(userId);
        } catch (Exception e) {
            Logger.error("InstantCache.getUserObject: userId: %s, ex: %s", userId, e);
            User ret = new User();
            ret.id = userId;
            ret.name = userId.toString();
            return ret;
        }
    }

    @Override
    protected byte[] downloadAsset(MessageAssetBase message) throws Exception {
        byte[] cipher;
        try {
            cipher = api.downloadAsset(message.getAssetKey(), message.getAssetToken());
        } catch (HttpException e) {
            if (e.getCode() == 401) {
                Access access = new LoginClient(client).login(email, password);
                this.api = new API(client, null, access.getToken());
                cipher = api.downloadAsset(message.getAssetKey(), message.getAssetToken());
            } else {
                throw e;
            }
        }
        byte[] sha256 = MessageDigest.getInstance("SHA-256").digest(cipher);
        if (!Arrays.equals(sha256, message.getSha256()))
            throw new Exception("Failed sha256 check");

        return Util.decrypt(message.getOtrKey(), cipher);
    }

    @Override
    protected User getUserInternal(UUID userId) throws HttpException {
        return api.getUser(userId);
    }

    @Override
    protected byte[] downloadProfilePicture(String key) throws Exception {
        return api.downloadAsset(key, null);
    }
}
