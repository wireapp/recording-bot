package com.wire.bots.recording;

import com.ning.http.util.Base64;
import com.wire.lithium.API;
import com.wire.xenon.assets.FileAsset;
import com.wire.xenon.models.AssetKey;
import com.wire.xenon.tools.Util;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import org.eclipse.jetty.util.UrlEncoded;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.client.Client;
import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

public class AssetTests {

    private DropwizardTestSupport support;

    private Client client;

    @Before
    public void setup() throws Exception {

        support = new DropwizardTestSupport<>(
                Service.class,
                "recording.yaml",
                ConfigOverride.config("token", "dummy"),
                ConfigOverride.config("healthchecks", "false")
        );

        support.before();

        final Service server = (Service) support.getApplication();
        client = server.getClient();
    }

    @After
    public void after() {
        support.after();
    }

    @Test
    public void downloadAssetTest() throws Exception {
        /*
        ```
        {
            "eventId":"2be2efec-5a59-4f56-8d2f-4155a73c4415",
            "messageId":"ed10eb10-c429-4387-89f7-ab27b1b25adf",
            "conversationId":"6519f09f-8c72-4397-9f6e-cd7da4ed7e78",
            "clientId":"1a955d5af444a8e3",
            "userId":"0a2203f9-b0c2-4dfe-a349-f668dfd1397b",
            "time":"2022-10-27T09:31:06.974Z",
            "assetId":"3-4-22d347d5-4e74-44f7-bf5f-d73838bffd79",
            "assetToken":"",
            "otrKey":"iRnwKWIhs/NjMPefUalDXYQuCJ24Cx/7GFrKYYWfrEU=",
            "sha256":"unFvlwnskg0I6pBzIVdF6844gS/EjjxoaAjRhRKXs6w="
        }
        ```
         */

        String botToken = "oRypYNXLKSzCzZRFLlupFnbnvjR_OuCIE7gW55b-nHruEqdecAKn9VW69VnNojaEIMB4fpVjQgCSib3nca52Aw==.v=1.k=1.d=-1.t=b.l=.p=d64af9ae-e0c5-4ce6-b38a-02fd9363b54c.b=8764b27e-ad87-433f-a6d9-6c41c20b9f81.c=6519f09f-8c72-4397-9f6e-cd7da4ed7e78";
        String assetId = "3-4-22d347d5-4e74-44f7-bf5f-d73838bffd79";
        String assetToken = "";
        byte[] otrKey = Base64.decode("iRnwKWIhs/NjMPefUalDXYQuCJ24Cx/7GFrKYYWfrEU=");
        byte[] sha256Challenge = Base64.decode("unFvlwnskg0I6pBzIVdF6844gS/EjjxoaAjRhRKXs6w=");

        API api = new API(client, botToken, "https://staging-nginz-https.zinfra.io");

        byte[] cipher = api.downloadAsset(assetId, assetToken);
        byte[] sha256 = MessageDigest.getInstance("SHA-256").digest(cipher);

        if (!Arrays.equals(sha256, sha256Challenge))
            throw new Exception("Failed sha256 check");

        byte[] asset = Util.decrypt(otrKey, cipher);
    }

    @Test
    public void uploadAssetTest() throws Exception {
        String botToken = "oRypYNXLKSzCzZRFLlupFnbnvjR_OuCIE7gW55b-nHruEqdecAKn9VW69VnNojaEIMB4fpVjQgCSib3nca52Aw==.v=1.k=1.d=-1.t=b.l=.p=d64af9ae-e0c5-4ce6-b38a-02fd9363b54c.b=8764b27e-ad87-433f-a6d9-6c41c20b9f81.c=6519f09f-8c72-4397-9f6e-cd7da4ed7e78";
        API api = new API(client, botToken, "https://staging-nginz-https.zinfra.io");

        File file = new File("recording.yaml");
        FileAsset fileAsset = new FileAsset(file, "image/jpeg", UUID.randomUUID());

        //{"domain":"staging.zinfra.io","key":"3-2-726da28c-7fa9-4be4-b818-d0939ce7b4a2","token":"3s7abpMBb9YcqhbRqU64fA=="}
        AssetKey assetKey = api.uploadAsset(fileAsset);

    }

    @Test
    public void hashTest() throws NoSuchAlgorithmException {
        String assetId = "3-4-22d347d5-4e74-44f7-bf5f-d73838bffd79";
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        messageDigest.update(assetId.getBytes());
        String hash = Base64.encode(messageDigest.digest());
        String encode = UrlEncoded.encodeString(hash);
    }
}
