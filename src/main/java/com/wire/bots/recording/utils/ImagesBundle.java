package com.wire.bots.recording.utils;

import com.wire.xenon.tools.Logger;
import com.wire.xenon.tools.Util;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.servlets.assets.AssetServlet;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class ImagesBundle extends AssetsBundle {
    public ImagesBundle(String resourcePath, String uriPath, String name) {
        super(resourcePath, uriPath, "index.htm", name);
    }

    @Override
    protected AssetServlet createServlet() {
        return new _AssetServlet(getResourcePath(), getUriPath(), getIndexFile(), StandardCharsets.UTF_8);
    }

    static class _AssetServlet extends AssetServlet {
        _AssetServlet(String resourcePath, String uriPath, @Nullable String indexFile, @Nullable Charset defaultCharset) {
            super(resourcePath, uriPath, indexFile, defaultCharset);
        }

        @Override
        protected URL getResourceURL(String path) {
            Logger.info("ImagesBundle: loading: %s", path);
            try {
                String format = String.format("file:/%s", path);

                File file = new File(format);
                if (!file.exists()) {
                    Logger.warning("ImagesBundle: file does not exist: %s", format);
                    return null;
                }
                return new URL(format);
            } catch (MalformedURLException e) {
                //Logger.error(e.toString());
                return null;
            }
        }

        @Override
        protected byte[] readResource(URL requestedResourceURL) throws IOException {
            try (InputStream inputStream = requestedResourceURL.openStream()) {
                return Util.toByteArray(inputStream);
            } catch (FileNotFoundException e) {
                Logger.warning("ImagesBundle: %s", e);
                return null;
            }
        }
    }
}
