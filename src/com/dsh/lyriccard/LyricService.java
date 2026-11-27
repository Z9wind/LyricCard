package com.dsh.lyriccard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.RemoteViews;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LyricService extends Service {
    private static final String CHANNEL = "lyriccard_fg";
    private static final int NOTIFY_ID = 0x4c47;
    private static final long TICK_MS = 250;
    private static final long WIDGET_MS = 200;

    private static final int[] WIDGET_LAYOUTS = {
            R.layout.widget_lyric_c0, R.layout.widget_lyric_c1, R.layout.widget_lyric_c2, R.layout.widget_lyric_c3, R.layout.widget_lyric_c4, R.layout.widget_lyric_c5, R.layout.widget_lyric_c6, R.layout.widget_lyric_c7, R.layout.widget_lyric_c8, R.layout.widget_lyric_c9, R.layout.widget_lyric_c10, R.layout.widget_lyric_c11, R.layout.widget_lyric_c12, R.layout.widget_lyric_c13, R.layout.widget_lyric_c14, R.layout.widget_lyric_c15, R.layout.widget_lyric_c16, R.layout.widget_lyric_c17, R.layout.widget_lyric_c18, R.layout.widget_lyric_c19, R.layout.widget_lyric_c20, R.layout.widget_lyric_c21, R.layout.widget_lyric_c22, R.layout.widget_lyric_c23, R.layout.widget_lyric_c24, R.layout.widget_lyric_c25, R.layout.widget_lyric_c26, R.layout.widget_lyric_c27, R.layout.widget_lyric_c28, R.layout.widget_lyric_c29, R.layout.widget_lyric_c30, R.layout.widget_lyric_c31, R.layout.widget_lyric_c32, R.layout.widget_lyric_c33, R.layout.widget_lyric_c34, R.layout.widget_lyric_c35
    };

    private static final int[] WIDGET_IT = {
            R.layout.widget_lyric_it_c0, R.layout.widget_lyric_it_c1, R.layout.widget_lyric_it_c2, R.layout.widget_lyric_it_c3,
            R.layout.widget_lyric_it_c4, R.layout.widget_lyric_it_c5, R.layout.widget_lyric_it_c6, R.layout.widget_lyric_it_c7,
            R.layout.widget_lyric_it_c8, R.layout.widget_lyric_it_c9, R.layout.widget_lyric_it_c10, R.layout.widget_lyric_it_c11,
            R.layout.widget_lyric_it_c12, R.layout.widget_lyric_it_c13, R.layout.widget_lyric_it_c14, R.layout.widget_lyric_it_c15,
            R.layout.widget_lyric_it_c16, R.layout.widget_lyric_it_c17, R.layout.widget_lyric_it_c18, R.layout.widget_lyric_it_c19,
            R.layout.widget_lyric_it_c20, R.layout.widget_lyric_it_c21, R.layout.widget_lyric_it_c22, R.layout.widget_lyric_it_c23,
            R.layout.widget_lyric_it_c24, R.layout.widget_lyric_it_c25, R.layout.widget_lyric_it_c26, R.layout.widget_lyric_it_c27,
            R.layout.widget_lyric_it_c28, R.layout.widget_lyric_it_c29, R.layout.widget_lyric_it_c30, R.layout.widget_lyric_it_c31,
            R.layout.widget_lyric_it_c32, R.layout.widget_lyric_it_c33, R.layout.widget_lyric_it_c34, R.layout.widget_lyric_it_c35,
    };
    private static final int[] WIDGET_TI = {
            R.layout.widget_lyric_ti_c0, R.layout.widget_lyric_ti_c1, R.layout.widget_lyric_ti_c2, R.layout.widget_lyric_ti_c3,
            R.layout.widget_lyric_ti_c4, R.layout.widget_lyric_ti_c5, R.layout.widget_lyric_ti_c6, R.layout.widget_lyric_ti_c7,
            R.layout.widget_lyric_ti_c8, R.layout.widget_lyric_ti_c9, R.layout.widget_lyric_ti_c10, R.layout.widget_lyric_ti_c11,
            R.layout.widget_lyric_ti_c12, R.layout.widget_lyric_ti_c13, R.layout.widget_lyric_ti_c14, R.layout.widget_lyric_ti_c15,
            R.layout.widget_lyric_ti_c16, R.layout.widget_lyric_ti_c17, R.layout.widget_lyric_ti_c18, R.layout.widget_lyric_ti_c19,
            R.layout.widget_lyric_ti_c20, R.layout.widget_lyric_ti_c21, R.layout.widget_lyric_ti_c22, R.layout.widget_lyric_ti_c23,
            R.layout.widget_lyric_ti_c24, R.layout.widget_lyric_ti_c25, R.layout.widget_lyric_ti_c26, R.layout.widget_lyric_ti_c27,
            R.layout.widget_lyric_ti_c28, R.layout.widget_lyric_ti_c29, R.layout.widget_lyric_ti_c30, R.layout.widget_lyric_ti_c31,
            R.layout.widget_lyric_ti_c32, R.layout.widget_lyric_ti_c33, R.layout.widget_lyric_ti_c34, R.layout.widget_lyric_ti_c35,
    };
    private static final int[] WIDGET_AI = {
            R.layout.widget_lyric_ai_c0, R.layout.widget_lyric_ai_c1, R.layout.widget_lyric_ai_c2, R.layout.widget_lyric_ai_c3,
            R.layout.widget_lyric_ai_c4, R.layout.widget_lyric_ai_c5, R.layout.widget_lyric_ai_c6, R.layout.widget_lyric_ai_c7,
            R.layout.widget_lyric_ai_c8, R.layout.widget_lyric_ai_c9, R.layout.widget_lyric_ai_c10, R.layout.widget_lyric_ai_c11,
            R.layout.widget_lyric_ai_c12, R.layout.widget_lyric_ai_c13, R.layout.widget_lyric_ai_c14, R.layout.widget_lyric_ai_c15,
            R.layout.widget_lyric_ai_c16, R.layout.widget_lyric_ai_c17, R.layout.widget_lyric_ai_c18, R.layout.widget_lyric_ai_c19,
            R.layout.widget_lyric_ai_c20, R.layout.widget_lyric_ai_c21, R.layout.widget_lyric_ai_c22, R.layout.widget_lyric_ai_c23,
            R.layout.widget_lyric_ai_c24, R.layout.widget_lyric_ai_c25, R.layout.widget_lyric_ai_c26, R.layout.widget_lyric_ai_c27,
            R.layout.widget_lyric_ai_c28, R.layout.widget_lyric_ai_c29, R.layout.widget_lyric_ai_c30, R.layout.widget_lyric_ai_c31,
            R.layout.widget_lyric_ai_c32, R.layout.widget_lyric_ai_c33, R.layout.widget_lyric_ai_c34, R.layout.widget_lyric_ai_c35,
    };

    private static volatile int sizeW = 0, sizeH = 0;
    private volatile boolean isForeground = false;
    private String lastCur = "";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<MediaSession.Token, MediaController> controllers = new HashMap<>();
    private final ExecutorService io = Executors.newCachedThreadPool();
    private MediaSessionManager msm;
    private MediaSessionManager.OnActiveSessionsChangedListener sessionsListener;
    private MediaController active;

    private String title = "";
    private String artist = "";
    private Lyric lyric;
    private String lyricKey = "";
    /** 某些 Provider 不写 lyricInfo，而是把当前歌词行写进标题，这里缓存该行。 */
    private String providerLine = null;
    /** 当前 lyric 是否只是 providerLine 的单行占位（在线歌词到位后会替换）。 */
    private boolean lyricIsProviderLine = false;
    private boolean fetching = false;

    private long lastPos = 0;
    private long lastWidgetAt = 0;
    private int lastIndex = -2;
    private int lastHueIndex = -1;
    private boolean showingA = true;
    private boolean flipInit = false;
    private int glowColor = 0xFFFFD68A;
    private int lastArtGen = Integer.MIN_VALUE;
    private int systemAccentColor = 0;
    private int lastPadKey = -1;
    private int lastCap = -1;

    private boolean running = false;
    private volatile boolean launcherVisible = true;
    private long lastVisibleCheck = 0;
    private String lastNotify = "";

    public static void start(Context c) {
        Intent i = new Intent(c, LyricService.class);
        try {
            c.startService(i);
        } catch (Exception e) {
            try { c.startService(i); } catch (Exception ignored) {}
        }
    }

    private final android.content.BroadcastReceiver refreshReceiver = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            lastPushKey = "";
            forceRefresh = true;
            main.removeCallbacks(tick);
            main.post(tick);
        }
    };

    private final android.content.BroadcastReceiver visibilityReceiver = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            launcherVisible = i.getBooleanExtra("visible", true);
            if (launcherVisible) {
                main.removeCallbacks(tick);
                main.post(tick);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            android.content.IntentFilter f = new android.content.IntentFilter("com.dsh.lyriccard.LAUNCHER");
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(visibilityReceiver, f, Context.RECEIVER_EXPORTED);
            else registerReceiver(visibilityReceiver, f);
        } catch (Throwable ignored) {}
        createChannel();
        registerSizeReceiver();
        // 设置变化（斜体、字号、对齐、配色等）需要让小组件重建。
        try {
            getSharedPreferences("settings", MODE_PRIVATE)
                    .registerOnSharedPreferenceChangeListener(new android.content.SharedPreferences.OnSharedPreferenceChangeListener() {
                        @Override public void onSharedPreferenceChanged(android.content.SharedPreferences sp, String key) {
                            lastPushKey = "";
                        }
                    });
        } catch (Throwable ignored) {}
        startLauncherWatcher();
        try {
            android.content.IntentFilter rf = new android.content.IntentFilter("com.dsh.lyriccard.REFRESH");
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(refreshReceiver, rf, Context.RECEIVER_EXPORTED);
            else registerReceiver(refreshReceiver, rf);
        } catch (Throwable ignored) {}
        try { LyriconBridge.get(this).register(); } catch (Throwable ignored) {}
        running = true;
        main.post(tick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "com.dsh.lyriccard.TOGGLE".equals(intent.getAction())) {
            boolean en = !enabled();
            getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("enabled", en).apply();
            lastNotify = "";
            notifyStatus(en ? "LyricCard" : "已暂停", en ? "歌词已开启" : "歌词已暂停");
        }
        ensureMediaListener();
        running = true;
        forceRefresh = true;
        main.removeCallbacks(tick);
        main.post(tick);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        try { LyriconBridge.get(this).unregister(); } catch (Throwable ignored) {}
        try { unregisterReceiver(visibilityReceiver); } catch (Throwable ignored) {}
        main.removeCallbacksAndMessages(null);
        io.shutdownNow();
        if (msm != null && sessionsListener != null) {
            try { msm.removeOnActiveSessionsChangedListener(sessionsListener); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "歌词卡片", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String t, String s) {
        int pi = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), pi);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(this, CHANNEL);
        else b = new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat_lyric)
                .setContentTitle(t)
                .setContentText(s)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open);
        return b.build();
    }

    private boolean enabled() {
        return getSharedPreferences("settings", MODE_PRIVATE).getBoolean("enabled", true);
    }

    private void notifyStatus(String t, String s) {
        // 通知已移除
    }

    private boolean hasNotificationAccess() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName() + "/" + MediaListener.class.getName());
    }

    private void ensureMediaListener() {
        if (msm != null) return;
        if (!hasNotificationAccess()) {
            notifyStatus("需要通知使用权", "打开 LyricGlass 完成授权");
            return;
        }
        msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        if (msm == null) return;
        final ComponentName cn = new ComponentName(this, MediaListener.class);
        sessionsListener = new MediaSessionManager.OnActiveSessionsChangedListener() {
            @Override public void onActiveSessionsChanged(List<MediaController> list) { syncSessions(list); }
        };
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, cn, main);
            syncSessions(msm.getActiveSessions(cn));
        } catch (SecurityException e) {
            notifyStatus("通知使用权未生效", "请在系统设置中重新授权");
        } catch (Exception ignored) {}
    }

    private void syncSessions(List<MediaController> list) {
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
        if (playing != null) active = playing;
        else if (active == null || !controllers.containsValue(active)) active = any;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;

            // 离开桌面就暂停读取和刷新，回桌面由可见性广播立即恢复，省电。

            android.content.SharedPreferences ps = getSharedPreferences("settings", MODE_PRIVATE);
            boolean pauseRoot = ps.getBoolean("pause_root", false);
            boolean pauseUsage = ps.getBoolean("pause_usage", false);
            boolean visible = true;
            if (pauseRoot) {
                // LSP 事件驱动；还没收到过事件时按可见处理，避免误暂停。
                visible = VisibilityService.lastEventAt == 0 || VisibilityService.lspVisible;
            } else if (pauseUsage) {
                visible = launcherFg;
            }
            if (!visible && !forceRefresh) {
                main.postDelayed(this, 250);
                return;
            }
            forceRefresh = false;
            long now = SystemClock.elapsedRealtime();
            long gap = lastTickAt == 0 ? 0 : now - lastTickAt;
            lastTickAt = now;
            if (gap > 1500) dbg("tickGap " + gap);
            try { refresh(); } catch (Exception ignored) {}
            main.postDelayed(this, TICK_MS);
        }
    };

    /** 日志已关闭，避免占用存储和内存。 */
    private void dbg(String msg) {}

    private void registerSizeReceiver() {
        try {
            android.content.BroadcastReceiver r = new android.content.BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    sizeW = i.getIntExtra("w", 0);
                    sizeH = i.getIntExtra("h", 0);
                    android.util.Log.i("LyricCardSize", "recv=" + sizeW + "x" + sizeH);
                }
            };
            android.content.IntentFilter f = new android.content.IntentFilter("com.dsh.lyriccard.SIZE");
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_EXPORTED);
            else registerReceiver(r, f);
        } catch (Throwable ignored) {}
    }

    /** 省电开关开启时，后台线程判断桌面是否在前台：有 root 用 root，否则用使用记录。 */
    private void startLauncherWatcher() {
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                while (running) {
                    try {
                        android.content.SharedPreferences ps = getSharedPreferences("settings", MODE_PRIVATE);
                        boolean useRoot = ps.getBoolean("pause_root", false);
                        boolean useUsage = ps.getBoolean("pause_usage", false);
                        if (useRoot) {
                            // 优先用 LSP 推来的可见性；没收到过事件时才用 su 兜底。
                            if (VisibilityService.lastEventAt > 0) {
                                launcherFg = VisibilityService.lspVisible;
                            } else {
                                long nowMs = SystemClock.elapsedRealtime();
                                if (rootState == -1 || (rootState == 0 && nowMs - lastRootCheck > 30000)) {
                                    lastRootCheck = nowMs;
                                    rootState = checkRoot();
                                }
                                launcherFg = rootState == 1 ? isLauncherTopByRoot() : isLauncherForeground();
                            }
                        } else if (useUsage) {
                            launcherFg = isLauncherForeground();
                        } else {
                            launcherFg = true;
                        }
                    } catch (Throwable ignored) {}
                    try { Thread.sleep(3000); } catch (InterruptedException e) { return; }
                }
            }
        }, "launcher-watch");
        t.setDaemon(true);
        t.start();
    }

    private int checkRoot() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            r.close();
            p.waitFor();
            if (line != null && line.contains("uid=0")) return 1;
        } catch (Throwable ignored) {}
        return 0;
    }

    private boolean isLauncherTopByRoot() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c",
                    "dumpsys activity activities | grep -m1 ResumedActivity"});
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            r.close();
            p.waitFor();
            return line != null && line.contains("com.android.launcher");
        } catch (Throwable t) {
            return true;
        }
    }

    private boolean isLauncherForeground() {
        try {
            android.app.usage.UsageStatsManager usm = (android.app.usage.UsageStatsManager)
                    getSystemService(USAGE_STATS_SERVICE);
            if (usm == null) return true;
            long now = System.currentTimeMillis();
            android.app.usage.UsageEvents ev = usm.queryEvents(now - 15000, now);
            if (ev == null) return true;
            android.app.usage.UsageEvents.Event e = new android.app.usage.UsageEvents.Event();
            String last = null;
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                if (e.getEventType() == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    last = e.getPackageName();
                }
            }
            // 最近 15 秒内没有前台事件时不要贸然暂停。
            if (last == null) return true;
            return "com.android.launcher".equals(last);
        } catch (Throwable t) {
            return true;
        }
    }

    private boolean monetEnabled() {
        return getSharedPreferences("settings", MODE_PRIVATE).getBoolean("monet", false);
    }

    private int monetColor() {
        return getSharedPreferences("settings", MODE_PRIVATE).getInt("monet_color", 0xFFFFD68A);
    }

    private int systemAccent() {
        if (systemAccentColor == 0) {
            try {
                systemAccentColor = getColor(android.R.color.system_accent1_200);
            } catch (Throwable ignored) {}
            if (systemAccentColor == 0) systemAccentColor = 0xFFFFD68A;
        }
        return systemAccentColor;
    }

    private long lastTickAt = 0;
    private long lastDbgAt = 0;
    private long lastFetchTry = 0;

    private volatile boolean reading = false;
    private volatile boolean forceRefresh = false;
    private volatile boolean launcherFg = true;
    private volatile int rootState = -1; // -1 未检测, 0 无, 1 有
    private long lastRootCheck = 0;
    private volatile long readingAt = 0;

    private static final class Snapshot {
        String t = "", a = "";
        long pos = 0;
        int st = PlaybackState.STATE_NONE;
        String lyricInfo = null;
        String providerLine = null;
        android.graphics.Bitmap art = null;
    }

    /** MediaController 的 Binder 调用在播放器卡顿时会阻塞，绝不能放主线程。 */
    private void readState(Snapshot s) {
        updateActive();
        MediaController act = active;
        if (act != null) {
            MediaMetadata md = act.getMetadata();
            PlaybackState ps = act.getPlaybackState();
            if (md != null) {
                String rawTitle = nz(md.getString(MediaMetadata.METADATA_KEY_TITLE));
                String rawArtist = nz(md.getString(MediaMetadata.METADATA_KEY_ARTIST));
                if (rawArtist.isEmpty()) rawArtist = nz(md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST));
                String dispTitle = nz(md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
                s.lyricInfo = md.getString("lyricInfo");
                s.t = rawTitle;
                s.a = rawArtist;
                if (s.lyricInfo == null || s.lyricInfo.isEmpty()) {
                    if (!dispTitle.isEmpty() && !dispTitle.equals(rawTitle)) {
                        s.t = dispTitle;
                        s.providerLine = rawTitle;
                    } else {
                        int sep = rawArtist.lastIndexOf(" - ");
                        if (sep > 0) {
                            String songName = rawArtist.substring(0, sep).trim();
                            String singer = rawArtist.substring(sep + 3).trim();
                            if (!songName.isEmpty() && !singer.isEmpty() && !songName.equals(rawTitle)) {
                                s.t = songName;
                                s.a = singer;
                                s.providerLine = rawTitle;
                            }
                        }
                    }
                }
                s.art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (s.art == null) s.art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            }
            if (ps != null) {
                s.st = ps.getState();
                s.pos = ps.getPosition();
                float speed = ps.getPlaybackSpeed() > 0 ? ps.getPlaybackSpeed() : 1f;
                if (s.st == PlaybackState.STATE_PLAYING) {
                    s.pos += (long) ((SystemClock.elapsedRealtime() - ps.getLastPositionUpdateTime()) * speed);
                }
            }
        }
    }

    private void refresh() {
        if (!enabled()) return;
        if (reading) {
            // 后台 Binder 卡住时不能让刷新永久停摆，超过 3 秒强制复位。
            if (SystemClock.elapsedRealtime() - readingAt < 3000) return;
            reading = false;
        }
        reading = true;
        readingAt = SystemClock.elapsedRealtime();
        ensureMediaListener();
        io.execute(new Runnable() {
            @Override public void run() {
                final Snapshot snap = new Snapshot();
                try { readState(snap); } catch (Throwable ignored) {}
                main.post(new Runnable() {
                    @Override public void run() {
                        reading = false;
                        try { applySnapshot(snap); } catch (Throwable ignored) {}
                    }
                });
            }
        });
    }

    private void applySnapshot(Snapshot snap) {
        String t = snap.t, a = snap.a;
        long pos = snap.pos;
        int st = snap.st;
        String lyricInfo = snap.lyricInfo;
        providerLine = snap.providerLine;
        android.graphics.Bitmap art = snap.art;
        LyriconBridge bridge = LyriconBridge.get(this);
        Lyric bridgeLyric = bridge.getLyric();
        // 播放器已经停止/退出（没有活动会话）时，不再使用桥里残留的旧歌词，
        // 否则会一直停在上一次的最后一句，而不是显示默认文本。
        if (active == null || st == PlaybackState.STATE_NONE || st == PlaybackState.STATE_STOPPED) {
            bridgeLyric = null;
            t = "";
            a = "";
            lyricInfo = null;
            providerLine = null;
        }
        if (bridgeLyric != null && !bridgeLyric.isEmpty()) {
            String bt = bridge.getTitle();
            if (bt != null && !bt.isEmpty()) {
                t = bt;
                a = bridge.getArtist();
            }
        }
        title = t;
        artist = a;

        if (t.isEmpty()) {
            glowColor = systemAccent();
        } else if (monetEnabled()) {
            glowColor = monetColor();
        } else if (art != null) {
            int gen = art.getGenerationId();
            if (gen != lastArtGen) {
                lastArtGen = gen;
                int color = Lyric.dominantColor(art);
                if (color != 0) {
                    glowColor = color;
                    getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("cover_color", color).apply();
                    try {
                        Intent ci = new Intent("com.dsh.lyriccard.COLOR");
                        ci.setPackage("com.android.launcher");
                        ci.putExtra("color", color);
                        sendBroadcast(ci);
                    } catch (Throwable ignored) {}
                }
            }
        }

        long dbgNow = SystemClock.elapsedRealtime();
        if (dbgNow - lastDbgAt > 5000) {
            lastDbgAt = dbgNow;
            dbg("state t=" + t + " a=" + a + " infoLen=" + (lyricInfo == null ? -1 : lyricInfo.length())
                    + " bridge=" + (bridgeLyric == null ? 0 : bridgeLyric.lines.size()) + "/" + (bridge.isConnected() ? 1 : 0));
        }
        String key = t + "\u0001" + a + "\u0001" + (lyricInfo == null ? "" : lyricInfo.hashCode());
        if (bridgeLyric != null && !bridgeLyric.isEmpty()) {
            // 桥的歌词优先：直接覆盖，避免在线匹配把 Provider 的歌词换掉。
            lyric = bridgeLyric;
            lyricIsProviderLine = false;
            lyricKey = key;
        } else if (!key.equals(lyricKey)) {
            dbg("keychange t=" + t + " a=" + a + " infoLen=" + (lyricInfo == null ? -1 : lyricInfo.length()) + " online=" + onlineEnabled());
            lyricKey = key;
            lastIndex = -2;
            flipInit = false;
            showingA = true;
            lyric = null;
            lyricIsProviderLine = false;
            boolean compat = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("compat", false);
            if (!compat && lyricInfo != null && !lyricInfo.isEmpty()) {
                try {
                    Lyric parsed = Lyric.parseLyricInfo(lyricInfo);
                    if (!parsed.isEmpty()) lyric = parsed;
                } catch (Exception ignored) {}
                if (lyric == null) {
                    try {
                        String raw = lyricInfo.trim();
                        if (raw.startsWith("[")) {
                            Lyric parsed = Lyric.parseLrc(raw, true);
                            if (!parsed.isEmpty()) lyric = parsed;
                        }
                    } catch (Throwable ignored) {}
                }
            }
            // Provider 只给当前行时先单行占位，在线歌词到位后会被替换。
            if (lyric == null && providerLine != null) {
                lyric = providerSingleLine(providerLine);
                lyricIsProviderLine = true;
            }
            if ((lyric == null || lyricIsProviderLine) && !t.isEmpty() && onlineEnabled()) {
                fetchOnline(t, a, 0, key, lyricInfo, active == null ? null : active.getPackageName());
            }
        } else if (providerLine != null && (lyric == null || lyricIsProviderLine)) {
            // 同一首歌内逐句更新单行占位，不重置已有歌词。
            if (lyric == null || lyric.lines.isEmpty() || !providerLine.equals(lyric.lines.get(0).text)) {
                lyric = providerSingleLine(providerLine);
                lyricIsProviderLine = true;
            }
        }

        if ((lyric == null || lyricIsProviderLine) && !t.isEmpty() && onlineEnabled()
                && SystemClock.elapsedRealtime() - lastFetchTry > 15000) {
            lastFetchTry = SystemClock.elapsedRealtime();
            fetchOnline(t, a, 0, key, lyricInfo, active == null ? null : active.getPackageName());
        }

        long displayPos;
        if (st == PlaybackState.STATE_PLAYING) { lastPos = pos; displayPos = pos; }
        else displayPos = lastPos;

        long now = SystemClock.elapsedRealtime();
        if (now - lastWidgetAt >= WIDGET_MS) {
            lastWidgetAt = now;
            pushWidget(displayPos);
        }
        updateForeground(st, t, a.isEmpty() ? "正在播放" : a);
    }

    private void updateForeground(int st, String t, String a) {
        try {
            boolean fg = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("foreground", true);
            boolean want = st == PlaybackState.STATE_PLAYING && fg;
            if (want && !isForeground) {
                startForeground(NOTIFY_ID, buildNotification(t.isEmpty() ? "LyricCard" : t, a));
                isForeground = true;
            } else if (want && isForeground) {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(NOTIFY_ID, buildNotification(t.isEmpty() ? "LyricCard" : t, a));
            } else if (!want && isForeground) {
                stopForeground(true);
                isForeground = false;
            }
        } catch (Throwable ignored) {}
    }

    private static String nz(String s) { return s == null ? "" : s; }

    /** 把 Provider 挤进标题的单行歌词包装成 Lyric，供现有显示逻辑复用。 */
    private static Lyric providerSingleLine(String text) {
        Lyric l = new Lyric();
        l.lines.add(new Lyric.Line(0, text));
        l.synced = false;
        return l;
    }

    private boolean onlineEnabled() {
        android.content.SharedPreferences sp = getSharedPreferences("settings", MODE_PRIVATE);
        return sp.getBoolean("online", false) || sp.getBoolean("compat", false);
    }

    private void fetchOnline(final String t, final String a, final long dur, final String key, final String info, final String pkg) {
        if (fetching) return;
        fetching = true;
        io.execute(new Runnable() {
            @Override public void run() {
                final Lyric l = LyricsFetcher.fetch(t, a, dur, pkg);
                dbg("fetch t=" + t + " a=" + a + " l=" + (l == null ? "null" : String.valueOf(l.lines.size())));
                main.post(new Runnable() {
                    @Override public void run() {
                        fetching = false;
                        if (!key.equals(lyricKey)) return;
                        if (l != null && !l.isEmpty()) {
                            lyric = l;
                            lyricIsProviderLine = false;
                        } else if (info != null && !info.isEmpty()) {
                            try {
                                Lyric fallback = Lyric.parseLyricInfo(info);
                                if (fallback != null && !fallback.isEmpty()) lyric = fallback;
                            } catch (Throwable ignored) {}
                        } else {
                            return;
                        }
                        lastIndex = -2;
                        flipInit = false;
                        pushWidget(lastPos);
                    }
                });
            }
        });
    }

    private float textHeight(float sizeDp) {
        android.graphics.Paint p = new android.graphics.Paint();
        p.setTextSize(sizeDp);
        android.graphics.Paint.FontMetrics fm = p.getFontMetrics();
        return Math.max(1f, fm.bottom - fm.top);
    }

    private int titleLines(String t, int wDp, float scale) {
        if (t == null || t.isEmpty()) return 1;
        android.graphics.Paint p = new android.graphics.Paint();
        p.setTextSize(13 * scale);
        return p.measureText(t) > wDp * 0.92f ? 2 : 1;
    }

    private int lineCount(android.graphics.Paint p, String text, float availW) {
        if (text == null || text.isEmpty()) return 0;
        float wd = p.measureText(text);
        if (availW <= 0) return 1;
        return Math.max(1, (int) Math.ceil(wd / availW));
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private int padPx(String key) {
        int def = "pad_bottom".equals(key) ? 4 : 14;
        return Math.round(getSharedPreferences("settings", MODE_PRIVATE).getInt(key, def)
                * getResources().getDisplayMetrics().density);
    }

    private String lastPushKey = "";

    private void pushWidget(long pos) {
        String cacheKey = pos / 1000 + "|" + title + "|" + artist + "|" + glowColor
                + "|" + (lyric == null ? -1 : lyric.indexAt(pos)) + "|" + lyricKey;
        if (cacheKey.equals(lastPushKey)) return;
        lastPushKey = cacheKey;
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(this, LyricWidgetProvider.class));
        if (ids == null || ids.length == 0) return;

        int w = 300, h = 110;
        if (sizeW > 0 && sizeH > 0) {
            float density = getResources().getDisplayMetrics().density;
            w = Math.round(sizeW / density);
            h = Math.round(sizeH / density);
            android.util.Log.i("LyricCardSize", "use=" + w + "x" + h);
        }
        try {
            Bundle o = mgr.getAppWidgetOptions(ids[0]);
            w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300);
            h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110);
            // 用当前实际尺寸（minWidth/minHeight），不用最大尺寸
        } catch (Exception ignored) {}

        String prev = "", cur = "", next = "", trans = "", prev2 = "", next2 = "";
        int idx = -1;
        if (lyric != null && !lyric.isEmpty()) {
            idx = lyric.indexAt(pos);
            if (idx >= 0) cur = lyric.lines.get(idx).text;
            if (idx > 0) prev = lyric.lines.get(idx - 1).text;
            if (idx + 1 < lyric.lines.size()) next = lyric.lines.get(idx + 1).text;
            if (idx >= 0) trans = transFor(lyric, lyric.lines.get(idx).time);
            if (idx > 1) prev2 = lyric.lines.get(idx - 2).text;
            if (idx + 2 < lyric.lines.size()) next2 = lyric.lines.get(idx + 2).text;
        }
        String emptyTitle = getSharedPreferences("settings", MODE_PRIVATE).getString("empty_title", "LyricCard");
        String emptyText = getSharedPreferences("settings", MODE_PRIVATE).getString("empty_text", "暂无播放");
        if (cur.isEmpty()) {
            if (!title.isEmpty()) cur = "\u266a " + title;
            else cur = emptyText;
        }

        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(glowColor | 0xFF000000, hsv);
        int hueIndex = Math.max(0, Math.min(35, (int) (hsv[0] / 10f)));
        int padKey = (padPx("pad_left") * 31 + padPx("pad_top")) * 31 + padPx("pad_right");
        padKey = padKey * 31 + padPx("pad_bottom");
        boolean layoutChanged = hueIndex != lastHueIndex || padKey != lastPadKey;
        if (layoutChanged) {
            lastPadKey = padKey;
            lastHueIndex = hueIndex;
            flipInit = false;
            showingA = true;
            lastIndex = -2;
        }

        int mode = getSharedPreferences("settings", MODE_PRIVATE).getInt("italic_mode", 0);
        int[] layouts = mode == 1 ? WIDGET_IT : mode == 2 ? WIDGET_TI : mode == 3 ? WIDGET_AI : WIDGET_LAYOUTS;
        RemoteViews rv = new RemoteViews(getPackageName(), layouts[hueIndex]);

        rv.setTextViewText(R.id.title, title.isEmpty() ? emptyTitle : title);
        rv.setTextViewText(R.id.artist, artist);
        float sw = w / 300f, sh = h / 110f;
        float scale = Math.max(0.72f, Math.min(1.6f, (float) Math.sqrt(sw * sh)));
        int fontScale = getSharedPreferences("settings", MODE_PRIVATE).getInt("font_scale", 100);
        scale *= Math.max(0.7f, Math.min(1.6f, fontScale / 100f));
        float rowScale = scale;
        float lineH = textHeight(17f);
        float titleH = textHeight(13 * rowScale) * titleLines(title, w, rowScale);
        float artistH = textHeight(10 * rowScale);
        float transH = trans.isEmpty() ? 0 : textHeight(11 * rowScale);
        float head = titleH + artistH + 10;
        int rows = (int) ((h - head - 10 - transH) / lineH);
        rows = Math.max(1, Math.min(24, rows));
        boolean tight = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("compact", false);

        int cap = Math.max(1, rows + 1);
        int n = (lyric == null) ? 0 : lyric.lines.size();
        android.graphics.Paint mp2 = new android.graphics.Paint();
        mp2.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11 * rowScale,
                getResources().getDisplayMetrics()));
        float availW = dpToPx(w) - dpToPx(28);
        int[] rowIds = {R.id.line_prev16, R.id.line_prev15, R.id.line_prev14, R.id.line_prev13, R.id.line_prev12, R.id.line_prev11, R.id.line_prev10, R.id.line_prev9, R.id.line_prev8, R.id.line_prev7, R.id.line_prev6, R.id.line_prev5, R.id.line_prev4, R.id.line_prev3, R.id.line_prev2, R.id.line_prev, R.id.text_a, R.id.line_next, R.id.line_next2, R.id.line_next3, R.id.line_next4, R.id.line_next5, R.id.line_next6, R.id.line_next7, R.id.line_next8, R.id.line_next9, R.id.line_next10, R.id.line_next11, R.id.line_next12, R.id.line_next13, R.id.line_next14, R.id.line_next15, R.id.line_next16};
        for (int k = 0; k < 33; k++) {
            if (k == 16) continue;
            rv.setViewVisibility(rowIds[k], View.GONE);
        }
        if (idx >= 0 && n > 0) {
            int curNeed = lineCount(mp2, cur, availW);
            int curShow = tight ? Math.min(curNeed, 2) : 8;
            int rem = Math.max(0, cap - Math.min(curNeed, 8));
            int upAvail = 0;
            for (int i = idx - 1; i >= 0 && (idx - i) <= 10; i--) {
                upAvail += lineCount(mp2, lyric.lines.get(i).text, availW);
            }
            int upBudget = 0, downBudget = 0;
            if (!tight) {
                upBudget = Math.min(rem / 2, upAvail);
                downBudget = rem - upBudget;
            }
            android.util.Log.i("LyricCardSize", "h=" + h + " rows=" + rows + " cap=" + cap
                    + " up=" + upBudget + " down=" + downBudget + " cur=" + curNeed);
            rv.setInt(R.id.text_a, "setMaxLines", curShow);
            rv.setInt(R.id.text_b, "setMaxLines", curShow);
            rv.setInt(R.id.glow_a, "setMaxLines", curShow);
            rv.setInt(R.id.glow_b, "setMaxLines", curShow);

            int used = 0;
            for (int i = idx - 1; i >= 0 && used < 16 && upBudget > 0; i--, used++) {
                String t = lyric.lines.get(i).text;
                int show = Math.min(lineCount(mp2, t, availW), upBudget);
                int viewId = rowIds[15 - used];
                rv.setTextViewText(viewId, t);
                rv.setInt(viewId, "setMaxLines", show);
                rv.setViewVisibility(viewId, View.VISIBLE);
                upBudget -= show;
            }
            used = 0;
            for (int i = idx + 1; i < n && used < 16 && downBudget > 0; i++, used++) {
                String t = lyric.lines.get(i).text;
                int show = Math.min(lineCount(mp2, t, availW), downBudget);
                int viewId = rowIds[17 + used];
                rv.setTextViewText(viewId, t);
                rv.setInt(viewId, "setMaxLines", show);
                rv.setViewVisibility(viewId, View.VISIBLE);
                downBudget -= show;
            }
        }
        rv.setTextViewText(R.id.line_trans, trans);
        rv.setViewVisibility(R.id.line_trans, trans.isEmpty() ? View.GONE : View.VISIBLE);

        SharedPreferences sp = getSharedPreferences("settings", MODE_PRIVATE);
        int align = sp.getInt("align", 1);
        boolean glow = sp.getBoolean("glow", true);
        boolean bg = sp.getBoolean("bg", true);
        int gravity = align == 0 ? (Gravity.LEFT | Gravity.CENTER_VERTICAL)
                : align == 2 ? (Gravity.RIGHT | Gravity.CENTER_VERTICAL)
                : (Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        rv.setInt(R.id.title, "setGravity", gravity);
        rv.setInt(R.id.artist, "setGravity", gravity);
        rv.setInt(R.id.content, "setGravity", gravity);
        for (int rid : rowIds) rv.setInt(rid, "setGravity", gravity);
        rv.setInt(R.id.line_trans, "setGravity", gravity);
        rv.setInt(R.id.text_a, "setGravity", gravity);
        rv.setInt(R.id.text_b, "setGravity", gravity);
        rv.setInt(R.id.glow_a, "setGravity", gravity);
        rv.setInt(R.id.glow_b, "setGravity", gravity);

        rv.setViewVisibility(R.id.bg_dark, bg ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.bg_light, bg ? View.GONE : View.VISIBLE);
        if (Build.VERSION.SDK_INT >= 31) {
            int alpha = sp.getInt("bg_alpha", 35);
            int bgView = bg ? R.id.bg_dark : R.id.bg_light;
            try { rv.setFloat(bgView, "setAlpha", Math.max(0, Math.min(100, alpha)) / 100f); } catch (Throwable ignored) {}
        }
        rv.setViewVisibility(R.id.glow_a, glow ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.glow_b, glow ? View.VISIBLE : View.GONE);
        rv.setViewPadding(R.id.content, padPx("pad_left"), padPx("pad_top"),
                padPx("pad_right"), padPx("pad_bottom"));


        rv.setTextViewTextSize(R.id.title, TypedValue.COMPLEX_UNIT_SP, 13 * scale);
        rv.setTextViewTextSize(R.id.artist, TypedValue.COMPLEX_UNIT_SP, 10 * scale);
        rv.setTextViewTextSize(R.id.line_prev, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_prev2, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_prev3, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_prev9, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_prev10, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_prev11, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_prev12, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_prev13, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_prev14, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_prev15, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_prev16, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_next9, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_next10, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_next11, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_next12, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_next13, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_next14, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_next15, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_next16, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_prev5, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_prev6, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_prev7, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_prev8, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_next5, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_next6, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_next7, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_next8, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_prev4, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_next, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_next2, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_next3, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_next4, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.line_trans, TypedValue.COMPLEX_UNIT_SP, 11 * scale);
        rv.setTextViewTextSize(R.id.text_a, TypedValue.COMPLEX_UNIT_SP, 17 * scale);
        rv.setTextViewTextSize(R.id.text_b, TypedValue.COMPLEX_UNIT_SP, 17 * scale);
        rv.setTextViewTextSize(R.id.glow_a, TypedValue.COMPLEX_UNIT_SP, 17 * scale);
        rv.setTextViewTextSize(R.id.glow_b, TypedValue.COMPLEX_UNIT_SP, 17 * scale);

        if (!flipInit) {
            rv.setTextViewText(R.id.text_a, cur);
            rv.setTextViewText(R.id.glow_a, cur);
            rv.setViewVisibility(R.id.layer_a, View.VISIBLE);
            rv.setViewVisibility(R.id.layer_b, View.GONE);
            flipInit = true;
            showingA = true;
        } else if (idx != lastIndex) {
            int layer = showingA ? R.id.layer_b : R.id.layer_a;
            int textId = showingA ? R.id.text_b : R.id.text_a;
            int glowId = showingA ? R.id.glow_b : R.id.glow_a;
            rv.setTextViewText(textId, cur);
            rv.setTextViewText(glowId, cur);
            rv.setViewVisibility(layer, View.VISIBLE);
            rv.setViewVisibility(showingA ? R.id.layer_a : R.id.layer_b, View.GONE);
            showingA = !showingA;
        } else {
            rv.setTextViewText(showingA ? R.id.text_a : R.id.text_b, cur);
            rv.setTextViewText(showingA ? R.id.glow_a : R.id.glow_b, cur);
        }
        lastIndex = idx;

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        rv.setOnClickPendingIntent(R.id.root,
                PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), flags));
        boolean fullUpdate = layoutChanged || cap != lastCap;
        lastCap = cap;
        for (int id : ids) {
            try {
                if (fullUpdate) mgr.updateAppWidget(id, rv);
                else mgr.partiallyUpdateAppWidget(id, rv);
            } catch (Throwable ignored) {}
        }
    }

    /** 按卡片高度估算可放下的歌词行数（标题之外）。 */
    private int estimateRows(int hDp, float scale, boolean hasTrans) {
        float head = 13 * scale * 1.25f + 10 * scale * 1.2f + 8;
        float lineH = 17 * scale * 1.25f;
        float body = hDp - head - 16;
        int rows = (int) (body / lineH);
        if (rows >= 4) return 5;
        if (rows >= 2) return 3;
        return 1;
    }

    private static boolean hasCjk(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) return true;
        }
        return false;
    }

    private String transFor(Lyric l, long time) {
        if (l.trans == null || l.trans.isEmpty()) return "";
        Lyric.Line best = null;
        long bestDelta = Long.MAX_VALUE;
        for (Lyric.Line t : l.trans) {
            long d = Math.abs(t.time - time);
            if (d < bestDelta) { bestDelta = d; best = t; }
        }
        if (best == null || bestDelta > 600) return "";
        return best.text;
    }
}
