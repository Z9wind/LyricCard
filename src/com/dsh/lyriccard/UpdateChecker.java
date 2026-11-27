package com.dsh.lyriccard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 检查 GitHub 更新。
 *
 * 优先直连 GitHub（用户自己开了代理也能走系统代理）；
 * 直连失败（超时/异常/非 200）才尝试镜像，不会一上来就用镜像。
 */
public class UpdateChecker {
    public interface Callback {
        /** ok=false 时 error 给出原因；ok=true 时 version/url 可用，notes 可能为空。 */
        void onResult(boolean ok, String version, String url, String notes, String error);
    }

    private static final String OWNER = "Z9wind";
    private static final String REPO = "LyricCard";
    private static final String GITHUB = "https://github.com/" + OWNER + "/" + REPO;
    private static final String API = "https://api.github.com/repos/" + OWNER + "/" + REPO;
    private static final String[] MIRRORS = {
            "https://ghfast.top/",
            "https://gh-proxy.com/"
    };

    public static String repoUrl() {
        return GITHUB;
    }

    public static void check(final Context ctx, final Callback cb) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override public void run() {
                Result r = null;
                String err = "";
                // 1) 直连
                try {
                    r = fetch(API + "/releases/latest", null);
                } catch (Throwable t) {
                    err = t.getClass().getSimpleName();
                }
                // 2) 直连失败才走镜像
                if (r == null) {
                    for (String m : MIRRORS) {
                        try {
                            r = fetch(API + "/releases/latest", m);
                            if (r != null) break;
                        } catch (Throwable ignored) {}
                    }
                }
                // 3) 没有 Release 时退回 tags
                if (r == null) {
                    try {
                        r = fetchTags(API + "/tags", null);
                    } catch (Throwable ignored) {}
                }
                final Result res = r;
                final String error = err;
                main.post(new Runnable() {
                    @Override public void run() {
                        if (res == null) {
                            cb.onResult(false, null, null, null, "网络不可用或无法连接 GitHub");
                        } else {
                            cb.onResult(true, res.version, res.url, res.notes, error);
                        }
                    }
                });
            }
        }, "update-check").start();
    }

    private static class Result {
        String version, url, notes;
    }

    private static Result fetch(String apiUrl, String mirror) throws Exception {
        String body = httpGet(mirror == null ? apiUrl : mirror + apiUrl);
        JSONObject o = new JSONObject(body);
        Result r = new Result();
        r.version = o.optString("tag_name", o.optString("name", ""));
        r.url = o.optString("html_url", GITHUB + "/releases");
        r.notes = o.optString("body", "");
        JSONArray assets = o.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a == null) continue;
                String name = a.optString("name", "");
                String dl = a.optString("browser_download_url", "");
                if (name.toLowerCase().endsWith(".apk") && !dl.isEmpty()) {
                    r.url = mirror == null ? dl : mirror + dl;
                    break;
                }
            }
        }
        if (r.version.isEmpty()) return null;
        return r;
    }

    private static Result fetchTags(String apiUrl, String mirror) throws Exception {
        String body = httpGet(mirror == null ? apiUrl : mirror + apiUrl);
        JSONArray arr = new JSONArray(body);
        if (arr.length() == 0) return null;
        JSONObject first = arr.getJSONObject(0);
        Result r = new Result();
        r.version = first.optString("name", "");
        r.url = GITHUB + "/releases";
        r.notes = "";
        if (r.version.isEmpty()) return null;
        return r;
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "LyricCard-Update");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IllegalStateException("HTTP " + code);
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }
}
