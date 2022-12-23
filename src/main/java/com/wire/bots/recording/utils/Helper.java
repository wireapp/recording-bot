package com.wire.bots.recording.utils;

import com.wire.xenon.tools.Logger;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javax.annotation.Nullable;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

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

    static Random rnd = new SecureRandom();

    private static File save(byte[] image, File file) throws IOException {
        try (DataOutputStream os = new DataOutputStream(new FileOutputStream(file))) {
            os.write(image);
        }
        Logger.info("Saved asset: %s", file.getAbsolutePath());
        return file;
    }

    static File saveAsset(String channelName, byte[] image, String assetKey, String mimeType) throws Exception {
        File file = assetFile(channelName, assetKey, mimeType);
        return save(image, file);
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
                .sanitizeUrls(true)
                .build()
                .render(document);
    }

    public static Long date(@Nullable String date) throws ParseException {
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        Date ret = parser.parse(date);
        return ret.getTime();
    }

    public static String key(String assetId, String salt) throws NoSuchAlgorithmException {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
        String value = salt + assetId + salt;
        messageDigest.update(value.getBytes());
        String encode = Base64.getEncoder().encodeToString(messageDigest.digest());
        return encode.replaceAll("[^a-zA-Z0-9]?", "");
    }

    static File assetFile(String channelName, String assetKey, String mimeType) {
        String extension = getExtension(mimeType);
        if (extension.isEmpty())
            extension = "error";
        String dirName = String.format("assets/%s", channelName);
        File dir = new File(dirName);
        dir.mkdir();
        String filename = String.format("%s/%s.%s", dirName, assetKey, extension);
        return new File(filename);
    }

    public static String getPdfFilename(String convName) {
        return String.format("html/%s.pdf", URLEncoder.encode(convName, StandardCharsets.UTF_8));
    }

    public static File getAssetDir(String channelName) throws NoSuchAlgorithmException {
        return new File(String.format("assets/%s", channelName));
    }

    public static String randomName(int len) {
        final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++)
            sb.append(AB.charAt(rnd.nextInt(AB.length())));
        return sb.toString();
    }

    public static String getHtmlFilename(String channelName) {
        return String.format("html/%s.html", channelName);
    }

    public static void deleteDir(File assetDir) {
        File[] files = assetDir.listFiles();
        if (files == null)
            return;

        for (File f : files) {
            if (f.isFile()) {
                boolean delete = f.delete();
                if (delete) {
                    String filename = f.getName();
                    String name = filename.split("\\.")[0];
                    Cache.removeAsset(name);
                }
                Logger.info("Deleted file: %s %s", f.getAbsolutePath(), delete);
            }
        }
    }
}
