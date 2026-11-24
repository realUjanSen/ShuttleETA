package com.example.shuttleeta;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Finds the latest GitHub release and downloads its APK. */
public final class Updater {
    public static final String REPO = "realUjanSen/ShuttleETA";

    public static final class Release {
        public final String tag;
        public final String apkName;
        public final String apkUrl;

        Release(String tag, String apkName, String apkUrl) {
            this.tag = tag;
            this.apkName = apkName;
            this.apkUrl = apkUrl;
        }
    }

    public interface Progress {
        void onProgress(int percent);
    }

    /** Returns the latest release that has an APK attached, or null if none. */
    public static Release fetchLatest() throws IOException {
        String body = get("https://api.github.com/repos/" + REPO + "/releases/latest");
        try {
            JSONObject json = new JSONObject(body);
            String tag = json.getString("tag_name");
            JSONArray assets = json.getJSONArray("assets");
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                String name = a.getString("name");
                if (name.endsWith(".apk")) {
                    return new Release(tag, name, a.getString("browser_download_url"));
                }
            }
            return null;
        } catch (org.json.JSONException e) {
            throw new IOException("Unexpected response from GitHub", e);
        }
    }

    /** Downloads the APK into dest, reporting progress as a percentage when the size is known. */
    public static void download(String url, File dest, Progress progress) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "ShuttleETA-updater");
        try (InputStream in = c.getInputStream();
             OutputStream out = new FileOutputStream(dest)) {
            long total = c.getContentLengthLong();
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                done += n;
                if (total > 0 && progress != null) {
                    progress.onProgress((int) (done * 100 / total));
                }
            }
        } finally {
            c.disconnect();
        }
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "ShuttleETA-updater");
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        try {
            int code = c.getResponseCode();
            if (code == 404) throw new IOException("No releases found yet");
            if (code != 200) throw new IOException("GitHub returned HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                return new String(readAll(in), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
