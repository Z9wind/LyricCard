package com.dsh.lyriccard;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

/**
 * 供桌面 LSP 模块直接通知「桌面是否可见」。
 *
 * 比广播可靠：不依赖广播投递，也不会因为进程重启丢状态；收到消息即可立即
 * 暂停/恢复刷新。无需轮询。
 */
public class VisibilityService extends Service {
    static final int MSG_VISIBLE = 1;
    /** 最近一次收到 LSP 事件的时间；0 表示还没收到过。 */
    static volatile long lastEventAt = 0;
    /** LSP 报告的桌面可见状态。 */
    static volatile boolean lspVisible = true;

    private final Messenger messenger = new Messenger(new Handler(android.os.Looper.getMainLooper()) {
        @Override public void handleMessage(Message msg) {
            if (msg.what == MSG_VISIBLE) {
                lspVisible = msg.arg1 != 0;
                lastEventAt = android.os.SystemClock.elapsedRealtime();
                Log.i("LyricVis", "lsp visible=" + lspVisible);
            }
        }
    });

    @Override public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }
}
