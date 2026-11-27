package com.dsh.lyriccard.xposed;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/** LSPosed 入口：只负责判断桌面是否在前台并发广播，不碰桌面视图。 */
public final class LauncherModule extends XposedModule {
    private static final String TAG = "LyricCardX";
    private static final String LAUNCHER = "com.android.launcher";
    private static final String LAUNCHER_CLASS = "com.android.launcher3.Launcher";

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!LAUNCHER.equals(param.getPackageName())) return;
        ClassLoader cl = param.getClassLoader();
        try {
            Class<?> launcher = Class.forName(LAUNCHER_CLASS, false, cl);
            int hooked = 0;
            for (final java.lang.reflect.Method m : launcher.getDeclaredMethods()) {
                final boolean isResume = "onResume".equals(m.getName()) && m.getParameterTypes().length == 0;
                final boolean isPause = "onPause".equals(m.getName()) && m.getParameterTypes().length == 0;
                if (!isResume && !isPause) continue;
                hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    Object self = chain.getThisObject();
                    if (self instanceof Activity) {
                        sendVisible((Activity) self, isResume);
                    }
                    return result;
                });
                hooked++;
            }
            log(Log.INFO, TAG, "hooked resume/pause=" + hooked);
            hookWidgetSize(cl);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook failed", t);
        }
    }

    /** 把歌词小组件的真实尺寸广播给 App（只读，不改视图）。 */
    private void hookWidgetSize(ClassLoader cl) {
        try {
            Class<?> host = Class.forName(
                    "com.android.launcher3.widget.CustomLauncherAppWidgetHostView", false, cl);
            int n = 0;
            for (final java.lang.reflect.Method m : host.getDeclaredMethods()) {
                if (!"onLayout".equals(m.getName())) continue;
                hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object r = chain.proceed();
                    try {
                        android.view.View v = (android.view.View) chain.getThisObject();
                        if (isLyricWidget(v)) sendSize(v.getContext(), v.getWidth(), v.getHeight());
                    } catch (Throwable ignored) {}
                    return r;
                });
                n++;
            }
            log(Log.INFO, TAG, "hooked widget size=" + n);
        } catch (Throwable t) {
            log(Log.WARN, TAG, "widget size hook skipped", t);
        }
    }

    private static boolean isLyricWidget(android.view.View v) {
        try {
            java.lang.reflect.Method m = v.getClass().getMethod("getAppWidgetId");
            int id = (Integer) m.invoke(v);
            android.appwidget.AppWidgetManager mgr =
                    android.appwidget.AppWidgetManager.getInstance(v.getContext());
            android.appwidget.AppWidgetProviderInfo info = mgr.getAppWidgetInfo(id);
            return info != null && info.provider != null
                    && "com.dsh.lyriccard".equals(info.provider.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    private void sendSize(android.content.Context ctx, int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (w == lastW && h == lastH) return;
        lastW = w; lastH = h;
        try {
            Intent i = new Intent("com.dsh.lyriccard.SIZE");
            i.setPackage("com.dsh.lyriccard");
            i.putExtra("w", w);
            i.putExtra("h", h);
            ctx.sendBroadcast(i);
        } catch (Throwable ignored) {}
    }

    private static int lastW, lastH;

    private static volatile android.os.Messenger visMessenger;
    private static volatile boolean pendingVisible = true;

    private static final android.content.ServiceConnection VIS_CONN =
            new android.content.ServiceConnection() {
        @Override public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
            try {
                visMessenger = new android.os.Messenger(service);
                sendVisibility(pendingVisible);
            } catch (Throwable ignored) {}
        }
        @Override public void onServiceDisconnected(android.content.ComponentName name) {
            visMessenger = null;
        }
    };

    private static void sendVisibility(boolean visible) {
        try {
            if (visMessenger == null) return;
            android.os.Message m = android.os.Message.obtain(null, 1);
            m.arg1 = visible ? 1 : 0;
            visMessenger.send(m);
        } catch (Throwable ignored) {}
    }

    private void sendVisible(Activity act, boolean visible) {
        pendingVisible = visible;
        try {
            if (visMessenger == null) {
                android.content.Intent si = new android.content.Intent();
                si.setComponent(new android.content.ComponentName(
                        "com.dsh.lyriccard", "com.dsh.lyriccard.VisibilityService"));
                act.bindService(si, VIS_CONN, android.content.Context.BIND_AUTO_CREATE);
            } else {
                sendVisibility(visible);
            }
        } catch (Throwable ignored) {}
        try {
            Intent i = new Intent("com.dsh.lyriccard.LAUNCHER");
            i.setPackage("com.dsh.lyriccard");
            i.putExtra("visible", visible);
            act.sendBroadcast(i);
        } catch (Throwable ignored) {}
        if (visible) {
            try {
                Intent si = new Intent();
                si.setComponent(new android.content.ComponentName(
                        "com.dsh.lyriccard", "com.dsh.lyriccard.LyricService"));
                act.startService(si);
            } catch (Throwable ignored) {}
        }
    }
}
