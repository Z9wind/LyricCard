package com.dsh.lyriccard.xposed;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** 把歌词卡片放进桌面 Workspace 的格子容器，随页面滚动。 */
final class LyricOverlay {
    static final String ACTION = "com.dsh.lyriccard.LYRIC";
    private static final String TAG = "LyricCardX.card";
    private static final String WORKSPACE = "Workspace";
    private static final String SHORTCUTS = "ShortcutAndWidgetContainer";
    private static final String CELL_LP = "com.android.launcher3.celllayout.CellLayoutLayoutParams";

    private static volatile View currentCard;
    private static volatile LyricCardView currentView;
    static volatile int manualColor = 0;
    static volatile int currentGlow = 0;
    private static volatile boolean pending = false;
    private static volatile Activity lastActivity;

    private LyricOverlay() {}

    static String attach(Activity act) {
        lastActivity = act;
        if (pending) return "pending";
        pending = true;
        String r = tryAttach(act, 0);
        if (!r.startsWith("retry")) pending = false;
        return r;
    }

    private static String tryAttach(Activity act, int attempt) {
        String actName = act.getClass().getName();
        View decor = act.getWindow() == null ? null : act.getWindow().getDecorView();
        ViewGroup workspace = findByClass(decor, WORKSPACE);
        if (workspace != null) { pending = false; return injectWorkspace(workspace); }
        if (decor != null && attempt < 15) {
            final int next = attempt + 1;
            decor.postDelayed(new Runnable() {
                @Override public void run() { tryAttach(act, next); }
            }, 400);
            return "retry-" + attempt;
        }
        Log.w(TAG, "workspace not found act=" + actName);
        return "no-workspace:" + actName;
    }

    static String injectWorkspace(ViewGroup workspace) {
        int page = 0;
        try {
            Method m = findMethod(workspace.getClass(), "getCurrentPage");
            if (m != null) { m.setAccessible(true); page = (Integer) m.invoke(workspace); }
        } catch (Throwable ignored) {}
        if (page < 0 || page >= workspace.getChildCount()) page = 0;
        View pageView = workspace.getChildAt(page);
        ViewGroup container = findShortcutContainer(pageView);
        if (container == null) {
            Log.w(TAG, "shortcut container not found page=" + page);
            return "no-container:page" + page;
        }
        if (container.findViewWithTag(TAG) != null) {
            return "already";
        }

        Context ctx = workspace.getContext().getApplicationContext();
        FrameLayout card = new FrameLayout(ctx);
        card.setTag(TAG);

        TextView debug = new TextView(ctx);
        debug.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        debug.setTextColor(0xCCFF7070);
        debug.setGravity(Gravity.CENTER);
        debug.setText("init");
        card.addView(debug, new FrameLayout.LayoutParams(-1, -2));

        LyricCardView view = new LyricCardView(ctx);
        card.addView(view, new FrameLayout.LayoutParams(-1, -2));

        int countY = 6;
        try {
            Field f = pageView.getClass().getDeclaredField("mCountY");
            f.setAccessible(true);
            countY = (Integer) f.get(pageView);
        } catch (Throwable ignored) {}
        int cellY = Math.max(0, countY - 2);

        try {
            ClassLoader cl = pageView.getClass().getClassLoader();
            Class<?> lpCls = Class.forName(CELL_LP, true, cl);
            Constructor<?> ctor = lpCls.getConstructor(int.class, int.class, int.class, int.class);
            Object lp = ctor.newInstance(0, cellY, 2, 2);
            container.addView(card, (ViewGroup.LayoutParams) lp);
        } catch (Throwable t) {
            Log.e(TAG, "add to cell failed", t);
            return "error-cell";
        }

        currentCard = card;
        currentView = view;
        Log.i(TAG, "card added to cell page=" + page + " y=" + cellY);

        registerColorReceiver(ctx);
        registerItalicReceiver(ctx, view);
        registerInjectReceiver(ctx);
        try {
            new LyricEngine(ctx, view, debug).start();
        } catch (Throwable t) {
            Log.e(TAG, "engine failed", t);
        }
        card.setAlpha(0f);
        card.animate().alpha(1f).setDuration(200).start();
        return "ok page=" + page + " y=" + cellY;
    }

    static void removeResidual(Activity act) {
        try {
            View decor = act.getWindow() == null ? null : act.getWindow().getDecorView();
            if (decor != null) removeByTag(decor);
        } catch (Throwable ignored) {}
    }

    private static void removeByTag(View v) {
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                View c = g.getChildAt(i);
                if (TAG.equals(c.getTag())) {
                    g.removeView(c);
                } else {
                    removeByTag(c);
                }
            }
        }
    }

    private static void registerInjectReceiver(Context ctx) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                Activity act = lastActivity;
                if (act != null) {
                    pending = false;
                    attach(act);
                }
            }
        };
        IntentFilter filter = new IntentFilter("com.dsh.lyriccard.INJECT");
        try {
            if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            else ctx.registerReceiver(receiver, filter);
        } catch (Throwable ignored) {}
    }

    private static void registerItalicReceiver(Context ctx, final LyricCardView view) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                view.setItalic(i.getBooleanExtra("cur", false), i.getBooleanExtra("other", false),
                        i.getBooleanExtra("title", false), i.getBooleanExtra("artist", false));
            }
        };
        IntentFilter filter = new IntentFilter("com.dsh.lyriccard.ITALIC");
        try {
            if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            else ctx.registerReceiver(receiver, filter);
        } catch (Throwable ignored) {}
    }

    private static void registerColorReceiver(Context ctx) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                int color = i.getIntExtra("color", 0);
                if (color == 0) {
                    manualColor = 0;
                    return;
                }
                manualColor = color;
                currentGlow = color;
                Log.i(TAG, "manual glow -> #" + Integer.toHexString(color));
            }
        };
        IntentFilter filter = new IntentFilter("com.dsh.lyriccard.COLOR");
        try {
            if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            else ctx.registerReceiver(receiver, filter);
        } catch (Throwable ignored) {}
    }








    private static Method findMethod(Class<?> cls, String name) {
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterTypes().length == 0) return m;
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static ViewGroup findByClass(View root, String className) {
        if (root == null) return null;
        String n = root.getClass().getName();
        if (root instanceof ViewGroup && (n.equals(className) || n.endsWith("." + className))) {
            return (ViewGroup) root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                ViewGroup found = findByClass(g.getChildAt(i), className);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static ViewGroup findShortcutContainer(View pageView) {
        if (pageView == null) return null;
        if (pageView.getClass().getName().endsWith(SHORTCUTS) && pageView instanceof ViewGroup) {
            return (ViewGroup) pageView;
        }
        if (pageView instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) pageView;
            for (int i = 0; i < g.getChildCount(); i++) {
                ViewGroup found = findShortcutContainer(g.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
