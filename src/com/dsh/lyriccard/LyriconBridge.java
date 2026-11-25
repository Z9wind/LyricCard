package com.dsh.lyriccard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 词幕（Lyricon）订阅桥。
 *
 * 这台设备上的 Provider 插件只把「当前这一句」写进媒体标题，完整歌词留在词幕自己的
 * 订阅通道里。这里按词幕 subscriber 的 AIDL 协议手工实现一个订阅端：
 * 向 SystemUI 里的中心服务注册，拿到 IRemoteService 后监听 onSongChanged，
 * 解析 Song JSON 得到完整歌词。
 */
public final class LyriconBridge {
    private static final String TAG = "LyriconBridge";
    private static final String ACTION_REGISTER = "io.github.proify.lyricon.lyric.bridge.REGISTER_SUBSCRIBER";
    private static final String ACTION_BOOT = "io.github.proify.lyricon.lyric.bridge.CENTRAL_BOOT_COMPLETED";
    private static final String EXTRA_BUNDLE = "bundle";
    private static final String EXTRA_BINDER = "binder";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String D_SUB = "io.github.proify.lyricon.subscriber.ISubscriberBinder";
    private static final String D_REMOTE = "io.github.proify.lyricon.subscriber.IRemoteService";
    private static final String D_LISTENER = "io.github.proify.lyricon.subscriber.IActivePlayerListener";
    private static final long RETRY_MS = 3000;
    private static final int MAX_RETRY = 3;

    private static LyriconBridge instance;

    public static synchronized LyriconBridge get(Context c) {
        if (instance == null) instance = new LyriconBridge(c.getApplicationContext());
        return instance;
    }

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener = new Listener();
    private boolean registered;
    private boolean connected;
    private int retryCount;
    private SubscriberBinder binderRef;
    private RemoteService remoteRef;

    private volatile Lyric lyric;
    private volatile String songTitle = "";
    private volatile String songArtist = "";
    private volatile long songDuration;

    private LyriconBridge(Context c) { ctx = c; }

    /** 桥拿到的完整歌词；没有时返回 null。 */
    public Lyric getLyric() { return lyric; }
    public String getTitle() { return songTitle; }
    public String getArtist() { return songArtist; }
    public long getDuration() { return songDuration; }
    public boolean isConnected() { return connected; }

    private final Runnable retry = new Runnable() {
        @Override public void run() {
            if (!registered || connected) return;
            if (retryCount++ >= MAX_RETRY) {
                Log.w(TAG, "register timeout, bridge unavailable");
                return;
            }
            sendRegister();
        }
    };

    private final BroadcastReceiver bootReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (ACTION_BOOT.equals(i.getAction())) {
                connected = false;
                retryCount = 0;
                sendRegister();
            }
        }
    };

    public synchronized void register() {
        if (registered) return;
        registered = true;
        try {
            IntentFilter f = new IntentFilter(ACTION_BOOT);
            if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(bootReceiver, f, Context.RECEIVER_EXPORTED);
            else ctx.registerReceiver(bootReceiver, f);
        } catch (Throwable t) {
            Log.w(TAG, "register boot receiver failed", t);
        }
        sendRegister();
    }

    public synchronized void unregister() {
        if (!registered) return;
        registered = false;
        connected = false;
        main.removeCallbacks(retry);
        try { ctx.unregisterReceiver(bootReceiver); } catch (Throwable ignored) {}
        RemoteService r = remoteRef;
        remoteRef = null;
        if (r != null) {
            r.unlinkDeath(remoteDeath);
            r.disconnectQuietly();
        }
        main.removeCallbacksAndMessages(null);
        lyric = null;
    }

    private void sendRegister() {
        main.removeCallbacks(retry);
        try {
            SubscriberBinder b = new SubscriberBinder();
            binderRef = b;
            Intent i = new Intent(ACTION_REGISTER);
            i.setPackage(SYSTEM_UI);
            Bundle bundle = new Bundle();
            bundle.putBinder(EXTRA_BINDER, b);
            i.putExtra(EXTRA_BUNDLE, bundle);
            ctx.sendBroadcast(i);
            main.postDelayed(retry, RETRY_MS);
        } catch (Throwable t) {
            Log.w(TAG, "send register failed", t);
        }
    }

    private final IBinder.DeathRecipient remoteDeath = new IBinder.DeathRecipient() {
        @Override public void binderDied() {
            main.post(new Runnable() {
                @Override public void run() {
                    connected = false;
                    remoteRef = null;
                    lyric = null;
                    if (registered) sendRegister();
                }
            });
        }
    };

    private void attach(RemoteService rs) {
        if (rs == null) return;
        final RemoteService remote = rs;
        main.post(new Runnable() {
            @Override public void run() {
                // 多词幕同时注册时保留先建立的稳定连接，避免来回覆盖。
                if (connected && remoteRef != null && remoteRef.isAlive()) return;
                main.removeCallbacks(retry);
                try {
                    remote.linkDeath(remoteDeath);
                    remote.setListener(listener);
                    remoteRef = remote;
                    connected = true;
                    Log.i(TAG, "bridge connected");
                } catch (Throwable t) {
                    Log.w(TAG, "set listener failed", t);
                    connected = false;
                }
            }
        });
    }

    private void onSongBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            lyric = null;
            return;
        }
        try {
            JSONObject o = new JSONObject(new String(bytes, "UTF-8"));
            String name = o.optString("name", "");
            String artist = o.optString("artist", "");
            long dur = o.optLong("duration", 0);
            JSONArray arr = o.optJSONArray("lyrics");
            Lyric l = new Lyric();
            l.synced = true;
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject line = arr.optJSONObject(i);
                    if (line == null) continue;
                    long begin = line.optLong("begin", 0);
                    String text = line.optString("text", "");
                    if (text.trim().isEmpty()) text = line.optString("secondary", "");
                    if (text.trim().isEmpty()) continue;
                    l.lines.add(new Lyric.Line(begin, text.trim()));
                    String tr = line.optString("translation", "");
                    if (!tr.trim().isEmpty()) l.trans.add(new Lyric.Line(begin, tr.trim()));
                }
            }
            l.sort();
            if (!l.lines.isEmpty()) {
                lyric = l;
                songTitle = name == null ? "" : name;
                songArtist = artist == null ? "" : artist;
                songDuration = dur;
            } else {
                lyric = null;
            }
        } catch (Throwable t) {
            Log.w(TAG, "parse song failed", t);
        }
    }

    private String subscriberInfo() {
        String pkg = ctx.getPackageName();
        String proc = pkg;
        try {
            if (Build.VERSION.SDK_INT >= 28) proc = android.app.Application.getProcessName();
        } catch (Throwable ignored) {}
        return "{\"packageName\":\"" + pkg + "\",\"processName\":\"" + proc + "\"}";
    }

    /** ISubscriberBinder.Stub 的手工实现。 */
    private final class SubscriberBinder extends Binder {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(D_SUB);
                return true;
            }
            switch (code) {
                case 1: { // onRegistrationCallback(IRemoteService service)
                    data.enforceInterface(D_SUB);
                    IBinder b = data.readStrongBinder();
                    if (reply != null) reply.writeNoException();
                    if (b != null) attach(new RemoteService(b));
                    return true;
                }
                case 2: { // getSubscriberInfo() -> byte[]
                    data.enforceInterface(D_SUB);
                    if (reply != null) {
                        reply.writeNoException();
                        reply.writeByteArray(subscriberInfo().getBytes());
                    }
                    return true;
                }
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }
    }

    /** IActivePlayerListener.Stub 的手工实现。 */
    private final class Listener extends Binder {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(D_LISTENER);
                return true;
            }
            data.enforceInterface(D_LISTENER);
            switch (code) {
                case 1: // onActiveProviderChanged(byte[])
                    data.createByteArray();
                    break;
                case 2: { // onSongChanged(byte[])
                    byte[] song = data.createByteArray();
                    onSongBytes(song);
                    break;
                }
                case 3: // onPlaybackStateChanged(boolean)
                    data.readInt();
                    break;
                case 4: // onSeekTo(long)
                    data.readLong();
                    break;
                case 5: // onReceiveText(String)
                    data.readString();
                    break;
                case 6: // onDisplayTranslationChanged(boolean)
                case 7: // onDisplayRomaChanged(boolean)
                    data.readInt();
                    break;
                default:
                    break;
            }
            if (reply != null) reply.writeNoException();
            return true;
        }
    }

    /** IRemoteService 的代理。 */
    private static final class RemoteService {
        private final IBinder binder;
        RemoteService(IBinder b) { binder = b; }

        void setListener(IBinder listener) throws RemoteException {
            Parcel d = Parcel.obtain();
            Parcel r = Parcel.obtain();
            try {
                d.writeInterfaceToken(D_REMOTE);
                d.writeStrongBinder(listener);
                binder.transact(1, d, r, 0);
                r.readException();
            } finally {
                r.recycle();
                d.recycle();
            }
        }

        boolean isAlive() { return binder.isBinderAlive(); }

        void linkDeath(IBinder.DeathRecipient d) {
            try { binder.linkToDeath(d, 0); } catch (Throwable ignored) {}
        }

        void unlinkDeath(IBinder.DeathRecipient d) {
            try { binder.unlinkToDeath(d, 0); } catch (Throwable ignored) {}
        }

        void disconnectQuietly() {
            Parcel d = Parcel.obtain();
            Parcel r = Parcel.obtain();
            try {
                d.writeInterfaceToken(D_REMOTE);
                binder.transact(3, d, r, 0);
            } catch (Throwable ignored) {
            } finally {
                r.recycle();
                d.recycle();
            }
        }
    }
}
