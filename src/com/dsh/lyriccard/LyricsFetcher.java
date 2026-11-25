package com.dsh.lyriccard;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

/** 在线歌词兜底：lrclib.net（无需 API key）。 */
public final class LyricsFetcher {
    private static final Map<String, Lyric> CACHE = new LinkedHashMap<String, Lyric>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Lyric> e) { return size() > 12; }
    };

    public static Lyric fetch(String title, String artist, long durationMs) {
        if (title == null || title.trim().isEmpty()) return null;
        String key = title.trim() + "\u0001" + (artist == null ? "" : artist.trim());
        synchronized (CACHE) {
            Lyric cached = CACHE.get(key);
            if (cached != null) return cached;
        }
        try {
            StringBuilder sb = new StringBuilder("https://lrclib.net/api/get?track_name=");
            sb.append(URLEncoder.encode(title.trim(), "UTF-8"));
            sb.append("&artist_name=").append(URLEncoder.encode(artist == null ? "" : artist.trim(), "UTF-8"));
            if (durationMs > 0) sb.append("&duration=").append(durationMs / 1000);
            HttpURLConnection c = (HttpURLConnection) new URL(sb.toString()).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "LyricGlass/1.0 (dsh)");
            int code = c.getResponseCode();
            if (code != 200) { c.disconnect(); return null; }
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) body.append(line).append('\n');
            r.close();
            c.disconnect();
            org.json.JSONObject o = new org.json.JSONObject(body.toString());
            String synced = o.optString("syncedLyrics", "");
            String plain = o.optString("plainLyrics", "");
            Lyric lyric = null;
            if (!synced.trim().isEmpty()) lyric = Lyric.parseLrc(synced, true);
            else if (!plain.trim().isEmpty()) lyric = Lyric.parseLrc(plain, false);
            if (lyric != null && !lyric.isEmpty()) {
                synchronized (CACHE) { CACHE.put(key, lyric); }
                return lyric;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
