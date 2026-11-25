package com.dsh.lyriccard;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** 快捷设置开关：控制歌词更新开/关。 */
public class LyricTile extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        if (getSharedPreferences("settings", MODE_PRIVATE).getBoolean("keep_alive", true)) {
            try { startService(new Intent(this, LyricService.class)); } catch (Throwable ignored) {}
        }
        update();
    }

    @Override
    public void onClick() {
        super.onClick();
        boolean keep = !getSharedPreferences("settings", MODE_PRIVATE).getBoolean("keep_alive", true);
        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("keep_alive", keep).apply();
        update();
        try {
            Intent i = new Intent(this, LyricService.class);
            if (keep) {
                startService(i);
            } else {
                stopService(i);
            }
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
