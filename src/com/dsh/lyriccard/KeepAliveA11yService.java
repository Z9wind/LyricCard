package com.dsh.lyriccard;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * 只用于保活的无障碍服务：不读取任何界面内容，也不做任何操作。
 *
 * ColorOS 的 OplusHansManager 会冻结普通后台服务（甚至无视 root 调整的 oom_score_adj），
 * 但系统绑定的无障碍服务不会被冻结。用户启用后，歌词服务进程能常驻，
 * 桌面小组件就不会停在旧的歌词上。
 */
public class KeepAliveA11yService extends AccessibilityService {
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override public void onInterrupt() {}

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        try {
            Intent i = new Intent(this, LyricService.class);
            i.putExtra("keep_alive", true);
            startService(i);
        } catch (Throwable ignored) {}
    }
}
