package com.dsh.lyriccard.xposed;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.dsh.lyriccard.Lyric;

/** 自绘歌词卡片：当前行按进度逐字发光，换行时平滑上移。 */
final class LyricCardView extends View {
    private final Paint main = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Lyric lyric;
    private long position;
    private int curIndex = -1;
    private int glowColor = 0xFFFFD68A;
    private float offsetY;
    private float lineHeight;
    private String title = "";
    private String artist = "";
    private boolean itCur = true, itOther = true, itTitle = true, itArtist = true;
    private static final Typeface BOLD = Typeface.DEFAULT_BOLD;
    private static final Typeface NORMAL = Typeface.DEFAULT;
    private static final Typeface BOLD_ITALIC = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.ITALIC);
    private static final Typeface NORMAL_ITALIC = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC);

    void setItalic(boolean cur, boolean other, boolean title, boolean artist) {
        itCur = cur; itOther = other; itTitle = title; itArtist = artist;
        android.util.Log.i("LyricCardX.card", "italic cur=" + cur + " other=" + other
                + " title=" + title + " artist=" + artist);
        invalidate();
    }
    private ValueAnimator anim;

    LyricCardView(Context ctx) {
        super(ctx);
        main.setTypeface(Typeface.DEFAULT_BOLD);
        main.setTextAlign(Paint.Align.LEFT);
        dim.setTypeface(Typeface.DEFAULT);
        dim.setTextAlign(Paint.Align.LEFT);
    }

    void update(Lyric l, long pos, int color, String song, String singer) {
        lyric = l;
        position = pos;
        glowColor = color;
        title = song == null ? "" : song;
        artist = singer == null ? "" : singer;
        int idx = (l == null || l.isEmpty()) ? -1 : l.indexAt(pos);
        if (idx != curIndex) {
            curIndex = idx;
            setAlpha(0f);
            animate().alpha(1f).setDuration(180).start();
        }
        invalidate();
    }


    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float base = Math.min(w / 18f, h / 4.6f);
        float cur = Math.max(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, getResources().getDisplayMetrics()),
                Math.min(base, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 26f, getResources().getDisplayMetrics())));
        main.setTextSize(cur);
        dim.setTextSize(cur * 0.62f);
        lineHeight = cur * 1.45f;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        float cx = w / 2f;
        float headTop = getHeight() * 0.04f;
        float headBottom = drawSongInfo(c, headTop);
        int cur = (lyric == null || lyric.isEmpty()) ? -1 : curIndex;
        if (lyric == null || lyric.isEmpty() || cur < 0) return;

        float lyricTop = headBottom + getHeight() * 0.02f;
        float lyricH = getHeight() - lyricTop;
        if (lyricH < 10f) return;
        float base = Math.min(w / 18f, lyricH / 4.2f);
        base = Math.max(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, getResources().getDisplayMetrics()),
                Math.min(base, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 24f, getResources().getDisplayMetrics())));
        main.setTextSize(base);
        dim.setTextSize(base * 0.62f);

        Paint.FontMetrics fmCur = main.getFontMetrics();
        Paint.FontMetrics fmDim = dim.getFontMetrics();
        float curSize = main.getTextSize();

        int idx = Math.max(0, Math.min(cur, lyric.lines.size() - 1));
        int n = lyric.lines.size();
        float centerY = lyricTop + lyricH / 2f;
        float lineH = curSize * 1.62f;
        int visible = Math.max(1, (int) (lyricH / lineH));
        int half = visible / 2;
        int start = Math.max(0, idx - half);
        int end = Math.min(n - 1, start + visible - 1);
        if (end - start + 1 < visible) start = Math.max(0, end - visible + 1);

        main.setTypeface(BOLD);
        dim.setTypeface(NORMAL);
        main.setTextSkewX(itCur ? -0.25f : 0f);
        dim.setTextSkewX(itOther ? -0.25f : 0f);
        for (int i = start; i <= end; i++) {
            float rowY = centerY + (i - idx) * lineH;
            if (i == idx) {
                drawCurrent(c, lyric.lines.get(i), cx, rowY - (fmCur.ascent + fmCur.descent) / 2f);
            } else {
                drawDim(c, lyric.lines.get(i).text, cx, rowY - (fmDim.ascent + fmDim.descent) / 2f);
            }
        }
        String tr = transFor(lyric.lines.get(idx).time);
        if (tr != null) {
            main.setTextSkewX(itCur ? -0.25f : 0f);
            float baseSize = main.getTextSize();
            main.setTextSize(baseSize * 0.72f);
            fit(main, tr);
            Paint.FontMetrics fmT = main.getFontMetrics();
            float trBaseline = (centerY + curSize * 0.55f) - (fmT.ascent + fmT.descent) / 2f;
            main.setColor(glowColor);
            main.setShadowLayer(9f, 0f, 0f, (0xC0 << 24) | (glowColor & 0xFFFFFF));
            c.drawText(tr, cx - main.measureText(tr) / 2f, trBaseline, main);
            main.setTextSize(baseSize);
        }
    }

    private float drawSongInfo(Canvas c, float top) {
        float left = getWidth() * 0.06f;
        float maxW = getWidth() * 0.88f;
        float titleSize = Math.max(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f,
                getResources().getDisplayMetrics()), Math.min(getWidth() / 11f, getHeight() / 9f));
        main.setTypeface(BOLD);
        main.setTextSkewX(itTitle ? -0.25f : 0f);
        main.setTextSize(titleSize);
        main.setColor(Color.WHITE);
        main.setShadowLayer(9f, 0f, 0f, (0xC0 << 24) | (glowColor & 0xFFFFFF));
        String t = title.isEmpty() ? "LyricCard" : title;
        float total = drawWrappedLeft(c, main, t, left, top, maxW);

        dim.setTypeface(NORMAL);
        dim.setTextSkewX(itArtist ? -0.25f : 0f);
        dim.setTextSize(titleSize * 0.78f);
        dim.setColor(0xCCFFFFFF);
        dim.setShadowLayer(7f, 0f, 0f, (0xA0 << 24) | (glowColor & 0xFFFFFF));
        String a = artist.isEmpty() ? "LyricCard" : artist;
        total += drawWrappedLeft(c, dim, a, left, top + total, maxW);
        return top + total;
    }

    private float drawWrappedLeft(Canvas c, Paint p, String text, float left, float topY, float maxW) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (line.length() > 0 && p.measureText(line.toString() + ch) > maxW) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(ch);
        }
        if (line.length() > 0) lines.add(line.toString());
        Paint.FontMetrics fm = p.getFontMetrics();
        float lh = (fm.descent - fm.ascent) * 1.18f;
        float y = topY + lh;
        for (String s : lines) {
            c.drawText(s, left, y, p);
            y += lh;
        }
        return lines.size() * lh;
    }

    /** 按宽度自动换行，返回占用高度。 */
    private float drawWrapped(Canvas c, Paint p, String text, float cx, float topY, float maxW) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (line.length() > 0 && p.measureText(line.toString() + ch) > maxW) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(ch);
        }
        if (line.length() > 0) lines.add(line.toString());
        Paint.FontMetrics fm = p.getFontMetrics();
        float lh = (fm.descent - fm.ascent) * 1.18f;
        float y = topY + lh;
        for (String s : lines) {
            c.drawText(s, cx - p.measureText(s) / 2f, y, p);
            y += lh;
        }
        return lines.size() * lh;
    }

    private String transFor(long time) {
        if (lyric == null || lyric.trans == null || lyric.trans.isEmpty()) return null;
        Lyric.Line best = null;
        long bestDelta = Long.MAX_VALUE;
        for (Lyric.Line t : lyric.trans) {
            long d = Math.abs(t.time - time);
            if (d < bestDelta) { bestDelta = d; best = t; }
        }
        if (best == null || bestDelta > 600) return null;
        return best.text;
    }

    private void drawDim(Canvas c, String text, float cx, float y) {
        if (text == null || text.isEmpty()) return;
        float base = dim.getTextSize();
        fit(dim, text);
        dim.setColor(0x66FFFFFF);
        dim.clearShadowLayer();
        c.drawText(text, cx - dim.measureText(text) / 2f, y, dim);
        dim.setTextSize(base);
    }

    /** 长行缩放到可用宽度内，避免被裁切。 */
    private void fit(Paint p, String text) {
        float base = p.getTextSize();
        float avail = getWidth() * 0.9f;
        float w = p.measureText(text);
        if (w > avail && w > 0) p.setTextSize(Math.max(base * 0.55f, base * avail / w));
    }

    private void drawCurrent(Canvas c, Lyric.Line line, float cx, float y) {
        float baseSize = main.getTextSize();
        fit(main, line.text);
        float total = main.measureText(line.text);
        float x = cx - total / 2f;
        int lit = -1;
        if (line.words != null) {
            for (int i = 0; i < line.words.size(); i++) {
                if (line.words.get(i).time <= position) lit = i;
                else break;
            }
        }
        if (line.words == null || line.words.isEmpty()) {
            boolean playing = position >= line.time;
            if (playing) {
                main.setColor(glowColor);
                main.setShadowLayer(11f, 0f, 0f, (0xC0 << 24) | (glowColor & 0xFFFFFF));
            } else {
                main.setColor(Color.WHITE);
                main.clearShadowLayer();
            }
            c.drawText(line.text, x, y, main);
            main.setTextSize(baseSize);
            return;
        }
        for (int i = 0; i < line.words.size(); i++) {
            Lyric.Word word = line.words.get(i);
            if (i <= lit) {
                main.setColor(glowColor);
                main.setShadowLayer(11f, 0f, 0f, (0xC0 << 24) | (glowColor & 0xFFFFFF));
            } else {
                main.setColor(Color.WHITE);
                main.clearShadowLayer();
            }
            c.drawText(word.text, x, y, main);
            x += main.measureText(word.text);
        }
        main.setTextSize(baseSize);
    }
}
