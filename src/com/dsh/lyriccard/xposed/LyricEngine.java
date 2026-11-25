package com.dsh.lyriccard.xposed;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;

import com.dsh.lyriccard.Lyric;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 在桌面进程内直接读 MediaSession，并本地插值刷新，不经过 App 中转。 */
final class LyricEngine {
    private static final String TAG = "LyricGlassX.engine";
    private static final long TICK = 50;
    private static final String LAUNCHER = "com.android.launcher";
    private static final String NOTIFY_LISTENER = "com.android.launcher3.notification.NotificationListener";

    private final Context ctx;
    private final LyricCardView card;
    private final TextView debug;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ComponentName listener;
    private final Map<MediaSession.Token, MediaController> controllers = new HashMap<>();

    private MediaSessionManager msm;
    private MediaController active;
    private MediaMetadata cachedMd;
    private PlaybackState cachedPs;
    private Lyric lyric;
    private String lyricKey = "";
    private long lastPos;
    private int lastIdx = -2;
    private boolean running;
    private int mdTick = 0;
    private int lastArtGeneration = Integer.MIN_VALUE;
    private String lastArtUri = "";
    private boolean hasArt = false;
    private int autoColor = 0xFFFFD68A;

    LyricEngine(Context ctx, LyricCardView card, TextView debug) {
        this.ctx = ctx;
        this.card = card;
        this.debug = debug;
        this.listener = new ComponentName(LAUNCHER, NOTIFY_LISTENER);
    }

    void start() {
        msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (msm == null) return;
        ComponentName auth = authComponent();
        List<MediaController> list = trySessions(auth);
        if (list == null && auth != null) list = trySessions(null);
        if (list == null) {
            Log.w(TAG, "cannot read MediaSession in launcher process");
            return;
        }
        try { msm.addOnActiveSessionsChangedListener(this::onSessions, auth, h); } catch (Throwable ignored) {}
        onSessions(list);
        running = true;
        h.post(tick);
        Log.i(TAG, "engine started, sessions=" + controllers.size());
    }

    private List<MediaController> trySessions(ComponentName auth) {
        try { return msm.getActiveSessions(auth); } catch (Throwable t) { return null; }
    }

    private ComponentName authComponent() {
        try {
            String flat = Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
            String target = LAUNCHER + "/" + NOTIFY_LISTENER;
            if (flat != null && flat.contains(target)) return listener;
        } catch (Throwable ignored) {}
        return null;
    }

    private void onSessions(List<MediaController> list) {
        controllers.clear();
        if (list != null) for (MediaController c : list) controllers.put(c.getSessionToken(), c);
        if (active != null && !controllers.containsValue(active)) active = null;
        if (active == null) updateActive();
    }

    private void updateActive() {
        MediaController playing = null;
        MediaController any = null;
        for (MediaController c : controllers.values()) {
            if (any == null) any = c;
            PlaybackState ps = c.getPlaybackState();
            if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) { playing = c; break; }
        }
        MediaController next = playing != null ? playing
                : (active != null && controllers.containsValue(active) ? active : any);
        if (next != active) {
            active = next;
            cachedMd = null;
            cachedPs = null;
            lyricKey = "";
            lastIdx = -2;
            if (active != null) {
                try {
                    cachedMd = active.getMetadata();
                    cachedPs = active.getPlaybackState();
                    active.registerCallback(new MediaController.Callback() {
                        @Override public void onMetadataChanged(MediaMetadata md) { cachedMd = md; }
                        @Override public void onPlaybackStateChanged(PlaybackState ps) { cachedPs = ps; }
                    }, h);
                } catch (Throwable ignored) {}
            }
        }
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            try { frame(); } catch (Throwable ignored) {}
            h.postDelayed(this, TICK);
        }
    };

    private void frame() {
        mdTick++;
        if (mdTick % 10 == 0) resyncSessions();
        updateActive();
        if (active == null) {
            card.update(null, 0, currentColor(), "", "");
            if (debug != null) debug.setText("NONE");
            return;
        }
        PlaybackState ps = cachedPs;
        try {
            PlaybackState fresh = active.getPlaybackState();
            if (fresh != null) { ps = fresh; cachedPs = fresh; }
        } catch (Throwable ignored) {}
        MediaMetadata md = cachedMd;
        if (md == null || mdTick % 10 == 0) {
            try {
                MediaMetadata fresh = active.getMetadata();
                if (fresh != null) { md = fresh; cachedMd = fresh; }
            } catch (Throwable ignored) {}
        }

        if (md != null) {
            android.graphics.Bitmap art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            String artUri = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
            if (artUri == null) artUri = md.getString(MediaMetadata.METADATA_KEY_ART_URI);
            if (art == null && artUri != null && !artUri.equals(lastArtUri)) {
                lastArtUri = artUri;
                art = loadUri(artUri);
            }
            int gen = art == null ? 0 : art.getGenerationId();
            if (art != null && gen != lastArtGeneration) {
                lastArtGeneration = gen;
                int auto = Lyric.dominantColor(art);
                if (auto != 0) autoColor = auto;
            }
            hasArt = art != null;
        }

        String title = md == null ? "" : nz(md.getString(MediaMetadata.METADATA_KEY_TITLE));
        String artist = md == null ? "" : nz(md.getString(MediaMetadata.METADATA_KEY_ARTIST));
        String info = md == null ? null : md.getString("lyricInfo");

        String key = title + "\u0001" + artist + "\u0001" + (info == null ? "" : info.hashCode());
        if (!key.equals(lyricKey)) {
            lyricKey = key;
            lyric = null;
            lastIdx = -2;
            if (info != null && !info.isEmpty()) {
                try {
                    Lyric parsed = Lyric.parseLyricInfo(info);
                    if (!parsed.isEmpty()) lyric = parsed;
                } catch (Throwable ignored) {}
            }
        }

        long pos = 0;
        int state = PlaybackState.STATE_NONE;
        if (ps != null) {
            pos = ps.getPosition();
            state = ps.getState();
            if (state == PlaybackState.STATE_PLAYING) {
                float speed = ps.getPlaybackSpeed() > 0 ? ps.getPlaybackSpeed() : 1f;
                pos += (long) ((SystemClock.elapsedRealtime() - ps.getLastPositionUpdateTime()) * speed);
            }
        }
        if (state == PlaybackState.STATE_PLAYING) lastPos = pos;
        else pos = lastPos;

        int idx = (lyric == null || lyric.isEmpty()) ? -1 : lyric.indexAt(pos);
        lastIdx = idx;

        if (debug != null) {
            String st = state == PlaybackState.STATE_PLAYING ? "PLAY"
                    : state == PlaybackState.STATE_PAUSED ? "PAUSE"
                    : state == PlaybackState.STATE_NONE ? "NONE" : "S" + state;
            debug.setText(st + " " + idx + "/" + (lyric == null ? 0 : lyric.lines.size())
                    + " " + (pos / 1000) + "s #" + Integer.toHexString(currentColor())
                    + (hasArt ? " a1" : " a0"));
        }
        card.update(lyric, pos, currentColor(), title, artist);
        if (debug != null) {
            String st = state == PlaybackState.STATE_PLAYING ? "PLAY"
                    : state == PlaybackState.STATE_PAUSED ? "PAUSE"
                    : state == PlaybackState.STATE_NONE ? "NONE" : "S" + state;
            debug.setText(st + " " + idx + "/" + (lyric == null ? 0 : lyric.lines.size())
                    + " " + (pos / 1000) + "s #" + Integer.toHexString(currentColor())
                    + (hasArt ? " a1" : " a0"));
        }
    }

    private int currentColor() {
        return LyricOverlay.manualColor != 0 ? LyricOverlay.manualColor : autoColor;
    }

    private void resyncSessions() {
        if (msm == null) return;
        try {
            List<MediaController> list = msm.getActiveSessions(authComponent());
            if (list != null) onSessions(list);
        } catch (Throwable ignored) {}
    }

    private android.graphics.Bitmap loadUri(String uri) {
        try {
            java.io.InputStream in = ctx.getContentResolver().openInputStream(android.net.Uri.parse(uri));
            if (in == null) return null;
            android.graphics.Bitmap b = android.graphics.BitmapFactory.decodeStream(in);
            in.close();
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
