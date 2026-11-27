package com.dsh.lyriccard;

import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** 快捷设置开关：控制歌词更新开/关；开启时必定以前台服务运行以保活。 */
public class LyricTile extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        if (getSharedPreferences("settings", MODE_PRIVATE).getBoolean("keep_alive", true)) {
            startKeepAlive();
        }
        update();
    }

    @Override
    public void onClick() {
        super.onClick();
        boolean keep = !getSharedPreferences("settings", MODE_PRIVATE).getBoolean("keep_alive", true);
        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("keep_alive", keep).apply();
        update();
        if (keep) {
            startKeepAlive();
        } else {
            try { stopService(new Intent(this, LyricService.class)); } catch (Throwable ignored) {}
        }
    }

    /** 拉起服务并让它用 root 调整进程优先级保活，不占用通知栏。 */
    private void startKeepAlive() {
        try {
            Intent i = new Intent(this, LyricService.class);
            i.putExtra("keep_alive", true);
            startService(i);
        } catch (Throwable ignored) {}
    }

    private void update() {
        try {
            Tile tile = getQsTile();
            if (tile == null) return;
            boolean keep = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("keep_alive", true);
            tile.setState(keep ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            if (Build.VERSION.SDK_INT >= 29) {
                tile.setLabel("歌词卡片");
            }
            tile.updateTile();
        } catch (Throwable ignored) {}
    }
}
