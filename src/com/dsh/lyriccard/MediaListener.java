package com.dsh.lyriccard;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.notification.NotificationListenerService;

/** 只作为 MediaSessionManager 的授权组件；真正的逻辑在 LyricService。 */
public class MediaListener extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        LyricService.start(getApplicationContext());
    }
}
