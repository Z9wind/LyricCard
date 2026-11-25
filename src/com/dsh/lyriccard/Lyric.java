package com.dsh.lyriccard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 歌词行与解析：支持标准 lyricInfo JSON、逐行 LRC、逐字 LRC。 */
public final class Lyric {
    public static final class Word {
        public final long time;
        public final String text;
        public Word(long time, String text) { this.time = time; this.text = text; }
    }

    public static final class Line {
        public final long time;
        public final String text;
        public final java.util.List<Word> words;
        public Line(long time, String text) { this(time, text, null); }
        public Line(long time, String text, java.util.List<Word> words) {
            this.time = time; this.text = text; this.words = words;
        }
    }

    public List<Line> lines = new ArrayList<>();
    public List<Line> trans = new ArrayList<>();
    public boolean synced = false;
    public String rawJson = null;

    public void sort() {
        Comparator<Line> c = new Comparator<Line>() {
            public int compare(Line a, Line b) { return Long.compare(a.time, b.time); }
        };
        Collections.sort(lines, c);
        Collections.sort(trans, c);
    }

    public boolean isEmpty() { return lines.isEmpty(); }

    public int indexAt(long positionMs) {
        if (lines.isEmpty()) return -1;
        int lo = 0, hi = lines.size() - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).time <= positionMs) { ans = mid; lo = mid + 1; }
            else hi = mid - 1;
        }
        return ans;
    }

    public static Lyric parseLrc(String lrc, boolean synced) {
        Lyric out = new Lyric();
        out.synced = synced;
        if (lrc == null) return out;
        for (String raw : lrc.replace("\r", "").split("\n")) {
            String s = raw.trim();
            if (s.isEmpty()) continue;
            // 取行首时间标签
            long t = -1;
            int p = 0;
            while (p < s.length() && (s.charAt(p) == '[' || s.charAt(p) == '<')) {
                int close = s.indexOf(s.charAt(p) == '[' ? ']' : '>', p);
                if (close < 0) break;
                String tag = s.substring(p + 1, close);
                long parsed = parseTime(tag);
                if (parsed >= 0 && t < 0) t = parsed;
                p = close + 1;
            }
            String rest = s.substring(Math.min(p, s.length()));
            java.util.List<Word> words = null;
            if (rest.contains("<")) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("<(\\d+:\\d+(?:[.:]\\d+)?)>([^<]*)").matcher(rest);
                java.util.ArrayList<Word> list = new java.util.ArrayList<>();
                while (m.find()) {
                    long wt = parseTime(m.group(1));
                    String wtxt = m.group(2);
                    if (wt >= 0 && wtxt != null && !wtxt.isEmpty()) list.add(new Word(wt, wtxt));
                }
                if (!list.isEmpty()) words = list;
            }
            String text = rest.replaceAll("<[^>]*>", "").trim();
            if (t >= 0 && !text.isEmpty()) out.lines.add(new Line(t, text, words));
        }
        out.sort();
        return out;
    }

    /** 解析 ColorOS/Bridge 的 lyricInfo JSON。 */
    public static Lyric parseLyricInfo(String json) throws Exception {
        Lyric out = new Lyric();
        out.rawJson = json;
        org.json.JSONObject o = new org.json.JSONObject(json);
        String raw = optString(o, "rawLyric", "rawLyric");
        String plain = optString(o, "lyric", "lrc");
        String trans = optString(o, "translationLyric", "translatedLyric", "transLyric");
        if (raw != null && raw.contains("<")) out = parseLrc(raw, true);
        else if (plain != null) out = parseLrc(plain, false);
        else if (raw != null) out = parseLrc(raw, false);
        out.rawJson = json;
        if (trans != null) out.trans = parseLrc(trans, false).lines;
        return out;
    }

    private static String optString(org.json.JSONObject o, String... keys) {
        for (String k : keys) {
            String v = o.optString(k, null);
            if (v != null && !v.trim().isEmpty()) return v;
        }
        return null;
    }

    /** 从封面提取主色：32x32 缩略图按色相分桶，取高饱和高权重簇平均色。 */
    public static int dominantColor(android.graphics.Bitmap bmp) {
        if (bmp == null) return 0;
        try {
            int size = 32;
            android.graphics.Bitmap s = android.graphics.Bitmap.createScaledBitmap(bmp, size, size, true);
            int[] px = new int[size * size];
            s.getPixels(px, 0, size, 0, 0, size, size);
            float[] hsv = new float[3];
            double[] w = new double[12];
            long[] rr = new long[12], gg = new long[12], bb = new long[12];
            for (int c : px) {
                if ((c >>> 24) < 128) continue;
                android.graphics.Color.colorToHSV(c, hsv);
                if (hsv[1] < 0.22f || hsv[2] < 0.15f || hsv[2] > 0.96f) continue;
                int h = Math.min(11, (int) (hsv[0] / 30f));
                double wt = hsv[1] * hsv[2];
                w[h] += wt;
                rr[h] += (long) (((c >> 16) & 0xFF) * wt);
                gg[h] += (long) (((c >> 8) & 0xFF) * wt);
                bb[h] += (long) ((c & 0xFF) * wt);
            }
            int best = -1;
            for (int i = 0; i < 12; i++) if (w[i] > 0.01 && (best < 0 || w[i] > w[best])) best = i;
            if (best < 0) return 0;
            int r = (int) (rr[best] / w[best]);
            int g = (int) (gg[best] / w[best]);
            int b = (int) (bb[best] / w[best]);
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        } catch (Throwable t) {
            return 0;
        }
    }

    static long parseTime(String tag) {
        // mm:ss.xxx 或 mm:ss:xx 或 hh:mm:ss.xxx
        try {
            int colon = tag.lastIndexOf(':');
            if (colon < 0) return -1;
            String mm = tag.substring(0, colon);
            String ss = tag.substring(colon + 1);
            int dot = ss.indexOf('.');
            if (dot < 0) dot = ss.indexOf(',');
            String frac = "";
            if (dot >= 0) { frac = ss.substring(dot + 1); ss = ss.substring(0, dot); }
            long m = Long.parseLong(mm.trim());
            double s = Double.parseDouble(ss.trim());
            double f = 0;
            if (!frac.isEmpty()) {
                frac = (frac + "000").substring(0, 3);
                f = Integer.parseInt(frac) / 1000.0;
            }
            return (long) ((m * 60 + s + f) * 1000);
        } catch (Exception e) {
            return -1;
        }
    }
}
