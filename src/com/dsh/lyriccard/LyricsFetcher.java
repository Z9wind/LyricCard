package com.dsh.lyriccard;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在线歌词兜底：按当前播放器优先匹配对应歌词源
 * （网易云 / QQ 音乐），最后再查 lrclib.net。
 */
public final class LyricsFetcher {
    private static final Map<String, Lyric> CACHE = new LinkedHashMap<String, Lyric>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Lyric> e) { return size() > 12; }
    };

    public static Lyric fetch(String title, String artist, long durationMs) {
        return fetch(title, artist, durationMs, null);
    }

    public static Lyric fetch(String title, String artist, long durationMs, String playbackPkg) {
        if (title == null || title.trim().isEmpty()) return null;
        String key = title.trim() + "\u0001" + (artist == null ? "" : artist.trim());
        synchronized (CACHE) {
            Lyric cached = CACHE.get(key);
            if (cached != null) return cached;
        }
        boolean isQQ = playbackPkg != null && playbackPkg.contains("qqmusic");
        // 先试与播放器对应的源
        if (isQQ) {
            Lyric q = fetchQQ(title, artist);
            if (q != null) return cacheAndReturn(key, q);
        } else {
            Lyric n = fetchNetease(title, artist);
            if (n != null) return cacheAndReturn(key, n);
        }
        // 再试另一个国内源
        if (isQQ) {
            Lyric n = fetchNetease(title, artist);
            if (n != null) return cacheAndReturn(key, n);
        } else {
            Lyric q = fetchQQ(title, artist);
            if (q != null) return cacheAndReturn(key, q);
        }
        // 最后 lrclib
        Lyric lr = fetchLrclib(title, artist, durationMs);
        if (lr != null) return cacheAndReturn(key, lr);
        return null;
    }

    private static Lyric cacheAndReturn(String key, Lyric l) {
        synchronized (CACHE) { CACHE.put(key, l); }
        return l;
    }

    /** 网易云公开接口（搜索 + 歌词 + 翻译）。 */
    private static Lyric fetchNetease(String title, String artist) {
        try {
            String q = URLEncoder.encode((title + " " + (artist == null ? "" : artist)).trim(), "UTF-8");
            String search = httpGet("https://music.163.com/api/search/get?s=" + q + "&type=1&limit=1", null);
            if (search.trim().isEmpty()) return null;
            org.json.JSONObject o = new org.json.JSONObject(search);
            org.json.JSONObject result = o.optJSONObject("result");
            org.json.JSONArray songs = result == null ? null : result.optJSONArray("songs");
            if (songs == null || songs.length() == 0) return null;
            long id = songs.getJSONObject(0).optLong("id");
            if (id <= 0) return null;
            String data = httpGet("https://music.163.com/api/song/lyric?id=" + id + "&lv=-1&tv=-1", null);
            if (data.trim().isEmpty()) return null;
            org.json.JSONObject lo = new org.json.JSONObject(data);
            org.json.JSONObject lrcObj = lo.optJSONObject("lrc");
            String lrc = lrcObj == null ? "" : lrcObj.optString("lyric", "");
            if (lrc.trim().isEmpty()) return null;
            org.json.JSONObject tObj = lo.optJSONObject("tlyric");
            String tlrc = tObj == null ? "" : tObj.optString("lyric", "");
            Lyric lyric = Lyric.parseLrc(lrc, true);
            if (lyric != null && !lyric.isEmpty() && !tlrc.trim().isEmpty()) {
                try {
                    Lyric tr = Lyric.parseLrc(tlrc, false);
                    if (tr != null && !tr.isEmpty()) lyric.trans = tr.lines;
                } catch (Throwable ignored) {}
            }
            return (lyric == null || lyric.isEmpty()) ? null : lyric;
        } catch (Throwable t) {
            return null;
        }
    }

    /** QQ 音乐公开接口（搜索 + 歌词），需要 Referer。 */
    private static Lyric fetchQQ(String title, String artist) {
        try {
            String q = URLEncoder.encode((title + " " + (artist == null ? "" : artist)).trim(), "UTF-8");
            String search = httpGet(
                    "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=" + q + "&format=json&n=1",
                    "https://y.qq.com/");
            if (search.trim().isEmpty()) return null;
            org.json.JSONObject so = new org.json.JSONObject(search);
            org.json.JSONObject data = so.optJSONObject("data");
            org.json.JSONObject song = data == null ? null : data.optJSONObject("song");
            org.json.JSONArray list = song == null ? null : song.optJSONArray("list");
            if (list == null || list.length() == 0) return null;
            String mid = list.getJSONObject(0).optString("songmid", "");
            if (mid.isEmpty()) return null;
            String lyr = httpGet(
                    "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid="
                            + URLEncoder.encode(mid, "UTF-8") + "&format=json&nobase64=1",
                    "https://y.qq.com/");
            if (lyr.trim().isEmpty()) return null;
            org.json.JSONObject lo = new org.json.JSONObject(lyr);
            String lrc = lo.optString("lyric", "");
            if (lrc.trim().isEmpty()) return null;
            Lyric lyric = Lyric.parseLrc(lrc, true);
            return (lyric == null || lyric.isEmpty()) ? null : lyric;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Lyric fetchLrclib(String title, String artist, long durationMs) {
        try {
            StringBuilder sb = new StringBuilder("https://lrclib.net/api/get?track_name=");
            sb.append(URLEncoder.encode(title.trim(), "UTF-8"));
            sb.append("&artist_name=").append(URLEncoder.encode(artist == null ? "" : artist.trim(), "UTF-8"));
            if (durationMs > 0) sb.append("&duration=").append(durationMs / 1000);
            String body = httpGet(sb.toString(), null);
            if (body.trim().isEmpty()) return null;
            org.json.JSONObject o = new org.json.JSONObject(body);
            String synced = o.optString("syncedLyrics", "");
            String plain = o.optString("plainLyrics", "");
            if (!synced.trim().isEmpty()) return Lyric.parseLrc(synced, true);
            if (!plain.trim().isEmpty()) return Lyric.parseLrc(plain, false);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String httpGet(String url, String referer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(4000);
        c.setReadTimeout(4000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0");
        if (referer != null) c.setRequestProperty("Referer", referer);
        try {
            if (c.getResponseCode() != 200) return "";
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) body.append(line).append('\n');
            r.close();
            return body.toString();
        } finally {
            c.disconnect();
        }
    }
}
