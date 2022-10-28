package com.wire.bots.recording.utils;

import com.wire.xenon.tools.Logger;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.eclipse.jetty.util.UrlEncoded;

import javax.annotation.Nullable;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class Helper {
    private static final List<Extension> extensions = Collections.singletonList(AutolinkExtension.create());

    private static final Parser parser = Parser
            .builder()
            .extensions(extensions)
            .build();

    static File saveProfileAsset(byte[] image, String key) throws Exception {
        String filename = avatarFile(key);
        File file = new File(filename);

        Logger.info("downloaded profile: %s, size: %d, file: %s", key, image.length, file.getAbsolutePath());
        return save(image, file);
    }

    static File saveAsset(byte[] image, String key) throws Exception {
        File file = assetFile(key, "image/jpeg");
        return save(image, file);
    }

    private static File save(byte[] image, File file) throws IOException {
        try (DataOutputStream os = new DataOutputStream(new FileOutputStream(file))) {
            os.write(image);
        }
        Logger.info("Saved asset: %s", file.getAbsolutePath());
        return file;
    }

    static File assetFile(String assetKey, String mimeType) {
        String extension = getExtension(mimeType);
        if (extension.isEmpty())
            extension = "error";
        String filename = String.format("assets/%s.%s", assetKey, extension);
        return new File(filename);
    }

    static String getExtension(String mimeType) {
        String[] split = mimeType.split("/");
        return split.length == 1 ? split[0] : split[1];
    }

    static String avatarFile(String key) {
        return String.format("avatars/%s.png", key);
    }

    @Nullable
    static String markdown2Html(@Nullable String text) {
        if (text == null)
            return null;
        Node document = parser.parse(text);
        return HtmlRenderer
                .builder()
                .escapeHtml(true)
                .extensions(extensions)
                .build()
                .render(document);
    }

    public static Long date(@Nullable String date) throws ParseException {
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        Date ret = parser.parse(date);
        return ret.getTime();
    }

    public static String key(String assetId) throws NoSuchAlgorithmException {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        messageDigest.update(assetId.getBytes());
        String encode = Base64.getEncoder().encodeToString(messageDigest.digest());
        return UrlEncoded.encodeString(encode);
    }
}
