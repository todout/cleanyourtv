package com.gathin.tvhome;

import android.app.Activity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class RemoteWallpapers {
    private static final long SYNC_INTERVAL_MS = 2 * 60 * 60 * 1000L;
    private static final String DEFAULT_URL =
            "https://raw.githubusercontent.com/todout/fondos-projectivy/wallpapers/wallpapers.json";
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static long lastSyncMs = 0L;

    private RemoteWallpapers() {}

    static void syncIfNeeded(Activity activity) {
        long now = System.currentTimeMillis();
        if (now - lastSyncMs < SYNC_INTERVAL_MS) return;
        lastSyncMs = now;

        EXEC.execute(() -> {
            try {
                File baseDir = activity.getExternalFilesDir(null);
                if (baseDir == null) return;
                String feedUrl = readFeedUrl(new File(baseDir, "config.json"));
                if (feedUrl == null || feedUrl.isEmpty()) return;

                String body = httpGetText(feedUrl);
                JSONArray arr = new JSONArray(body);
                if (arr.length() == 0) return;

                File wallpapersDir = new File(baseDir, "wallpapers");
                if (!wallpapersDir.exists() && !wallpapersDir.mkdirs()) return;

                Set<String> expectedNames = new HashSet<>();
                boolean changed = false;
                int okCount = 0;

                for (int i = 0; i < arr.length(); i++) {
                    String imgUrl;
                    Object item = arr.get(i);
                    if (item instanceof String) {
                        imgUrl = (String) item;
                    } else if (item instanceof JSONObject) {
                        JSONObject obj = (JSONObject) item;
                        imgUrl = obj.optString("url_img", obj.optString("url", ""));
                    } else {
                        continue;
                    }
                    if (imgUrl.isEmpty()) continue;

                    String name = sanitizeFilename(imgUrl);
                    expectedNames.add(name);
                    File target = new File(wallpapersDir, name);
                    if (target.exists() && target.length() > 0) {
                        okCount++;
                        continue;
                    }
                    File tmp = new File(wallpapersDir, name + ".tmp");
                    if (downloadFile(imgUrl, tmp)) {
                        if (target.exists()) target.delete();
                        if (tmp.renameTo(target)) {
                            okCount++;
                            changed = true;
                        }
                    } else {
                        tmp.delete();
                    }
                }

                if (okCount > 0) {
                    File[] existing = wallpapersDir.listFiles();
                    if (existing != null) {
                        for (File f : existing) {
                            String n = f.getName();
                            if (n.endsWith(".tmp")) {
                                f.delete();
                            } else if (!expectedNames.contains(n) && isImage(n)) {
                                if (f.delete()) changed = true;
                            }
                        }
                    }
                }

                if (changed) {
                    activity.runOnUiThread(() -> {
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            activity.recreate();
                        }
                    });
                }
            } catch (Exception ignored) {
                lastSyncMs = 0L;
            }
        });
    }

    private static String readFeedUrl(File configFile) {
        if (!configFile.exists()) return DEFAULT_URL;
        try {
            byte[] bytes = new byte[(int) Math.min(configFile.length(), 65536)];
            try (InputStream in = new java.io.FileInputStream(configFile)) {
                int read = in.read(bytes);
                if (read <= 0) return DEFAULT_URL;
                JSONObject obj = new JSONObject(new String(bytes, 0, read, StandardCharsets.UTF_8));
                if (obj.has("remote_wallpapers_url")) {
                    return obj.optString("remote_wallpapers_url", "");
                }
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_URL;
    }

    private static String sanitizeFilename(String url) {
        int q = url.indexOf('?');
        String clean = q >= 0 ? url.substring(0, q) : url;
        int slash = clean.lastIndexOf('/');
        String name = slash >= 0 ? clean.substring(slash + 1) : clean;
        name = name.replaceAll("[^A-Za-z0-9_.-]", "_");
        if (name.isEmpty()) name = "wallpaper.jpg";
        return name;
    }

    private static boolean isImage(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    private static String httpGetText(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", "CleanYourTV/1.0");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private static boolean downloadFile(String urlStr, File dest) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "CleanYourTV/1.0");
            if (conn.getResponseCode() != 200) return false;
            try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
            }
            return dest.length() > 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
