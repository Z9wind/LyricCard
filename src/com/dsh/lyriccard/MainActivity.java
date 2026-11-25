package com.dsh.lyriccard;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;
    private SharedPreferences sp;
    private int accent = 0xFF3D6BFF;

    private static final int ON_SURFACE = 0xFFE6E1E5;
    private static final int ON_VARIANT = 0xFF938F99;
    private int surface = 0xFF141218;
    private int container = 0xFF211F26;
    private final java.util.Map<String, android.widget.SeekBar> sliders = new java.util.HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("settings", MODE_PRIVATE);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyRecents();
        updateStatus();
        LyricService.start(this);
        sendItalic();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
        }
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private int onPrimary() {
        double lum = 0.2126 * Color.red(accent) + 0.7152 * Color.green(accent) + 0.0722 * Color.blue(accent);
        return lum > 140 ? 0xFF141218 : 0xFFFFFFFF;
    }

    private int neutral(int res, int fallback) {
        try {
            int c = getColor(res);
            return (c >>> 24) != 0 ? c : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private void buildUi() {
        accent = readSystemAccent();
        surface = neutral(android.R.color.system_neutral1_900, 0xFF141218);
        container = neutral(android.R.color.system_neutral2_800, 0xFF211F26);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(surface);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(36));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("LyricCard");
        title.setTextColor(ON_SURFACE);
        title.setTextSize(30);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("桌面歌词卡片");
        sub.setTextColor(accent);
        sub.setTextSize(14);
        sub.setPadding(0, dp(4), 0, dp(10));
        root.addView(sub);

        status = new TextView(this);
        status.setTextColor(ON_VARIANT);
        status.setTextSize(13);
        LinearLayout statusCard = card(root);
        statusCard.addView(status);

        LinearLayout quick = card(root);
        addButton(quick, "授予通知使用权", new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
                catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            }
        });
        addButton(quick, "添加桌面卡片", new View.OnClickListener() {
            @Override public void onClick(View v) { requestPin(); }
        });
        addButton(quick, "授予使用情况访问（省电判断）", new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                } catch (Throwable ignored) {}
            }
        });
        addButton(quick, "忽略电池优化", new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            }
        });

        groupTitle(root, "歌词颜色");
        LinearLayout colors = card(root);
        final View swatch = new View(this);
        GradientDrawable sw = new GradientDrawable();
        sw.setColor(sp.getInt("monet_color", 0xFFFFD68A) | 0xFF000000);
        sw.setCornerRadius(dp(12));
        swatch.setBackground(sw);
        colors.addView(swatch, new LinearLayout.LayoutParams(-1, dp(44)));

        Switch monetSw = new Switch(this);
        monetSw.setText("使用系统莫奈色（关闭则跟随封面）");
        monetSw.setTextColor(ON_SURFACE);
        monetSw.setTextSize(14);
        monetSw.setChecked(sp.getBoolean("monet", false));
        tintSwitch(monetSw);
        monetSw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean v) {
                sp.edit().putBoolean("monet", v).apply();
                try {
                    Intent ci = new Intent("com.dsh.lyriccard.COLOR");
                    ci.setPackage("com.android.launcher");
                    ci.putExtra("color", v ? sp.getInt("monet_color", 0xFFFFD68A) : 0);
                    sendBroadcast(ci);
                } catch (Throwable ignored) {}
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(10);
        colors.addView(monetSw, slp);

        addButton(colors, "读取系统莫奈色并应用", new View.OnClickListener() {
            @Override public void onClick(View v) {
                int color = readSystemAccent();
                sp.edit().putInt("monet_color", color).putBoolean("monet", true).apply();
                GradientDrawable d = new GradientDrawable();
                d.setColor(color | 0xFF000000);
                d.setCornerRadius(dp(12));
                swatch.setBackground(d);
                try {
                    Intent ci = new Intent("com.dsh.lyriccard.COLOR");
                    ci.setPackage("com.android.launcher");
                    ci.putExtra("color", color);
                    sendBroadcast(ci);
                } catch (Throwable ignored) {}
            }
        });

        groupTitle(root, "外观");
        LinearLayout look = card(root);
        addSwitch(look, "当前行发光", "glow", true);
        addSwitch(look, "深色背景（关闭为浅色）", "bg", true);
        addSwitch(look, "无歌词时在线匹配", "online", true);
        addSwitch(look, "紧凑模式（4×1 等矮卡片）", "compact", false);
        addSwitch(look, "隐藏最近任务卡片", "hide_recents", true);
        addItalicMode(look);

        TextView alignLabel = new TextView(this);
        alignLabel.setText("对齐方式");
        alignLabel.setTextColor(ON_VARIANT);
        alignLabel.setTextSize(12);
        alignLabel.setPadding(0, dp(12), 0, 0);
        look.addView(alignLabel);
        android.widget.RadioGroup group = new android.widget.RadioGroup(this);
        group.setOrientation(android.widget.RadioGroup.HORIZONTAL);
        int align = sp.getInt("align", 1);
        for (int i = 0; i < 3; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setId(2000 + i);
            rb.setText(i == 0 ? "左对齐" : i == 1 ? "居中" : "右对齐");
            rb.setTextColor(ON_SURFACE);
            rb.setTextSize(13);
            rb.setButtonTintList(ColorStateList.valueOf(accent));
            group.addView(rb);
        }
        group.check(2000 + align);
        group.setOnCheckedChangeListener(new android.widget.RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.RadioGroup g, int id) {
                sp.edit().putInt("align", id - 2000).apply();
            }
        });
        look.addView(group);

        addSlider(look, "背景不透明度", "bg_alpha", 100, 35);
        addFontSlider(look);

        groupTitle(root, "自定义文本");
        LinearLayout texts = card(root);
        addEdit(texts, "无歌曲时标题", "empty_title", "LyricCard");
        addEdit(texts, "暂无播放提示", "empty_text", "暂无播放");

        groupTitle(root, "内容边距");
        LinearLayout pads = card(root);
        addSlider(pads, "左边距", "pad_left", 40, 14);
        addSlider(pads, "上边距", "pad_top", 40, 14);
        addSlider(pads, "右边距", "pad_right", 40, 14);
        addSlider(pads, "下边距", "pad_bottom", 40, 14);
        addButton(pads, "恢复默认边距（14dp）", new View.OnClickListener() {
            @Override public void onClick(View v) {
                for (String k : new String[]{"pad_left", "pad_top", "pad_right", "pad_bottom"}) {
                    sp.edit().putInt(k, 14).apply();
                    android.widget.SeekBar b = sliders.get(k);
                    if (b != null) b.setProgress(14);
                }
            }
        });

        LinearLayout restartCard = card(root);
        addButton(restartCard, "重启桌面", new View.OnClickListener() {
            @Override public void onClick(View v) { restartLauncher(); }
        });

        TextView tip = new TextView(this);
        tip.setText("卡片大小在桌面上拖动边缘调整；修改外观后点“重启桌面”生效。");
        tip.setTextColor(ON_VARIANT);
        tip.setTextSize(12);
        tip.setLineSpacing(dp(3), 1f);
        tip.setPadding(dp(4), dp(18), dp(4), 0);
        root.addView(tip);

        setContentView(scroll);
    }

    private LinearLayout card(LinearLayout root) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(14), dp(18), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(container);
        bg.setCornerRadius(dp(24));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        root.addView(card, lp);
        return card;
    }

    private void groupTitle(LinearLayout root, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(accent);
        tv.setTextSize(13);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(dp(6), dp(20), 0, dp(2));
        root.addView(tv);
    }

    private void addButton(LinearLayout root, String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(onPrimary());
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(accent);
        bg.setCornerRadius(dp(22));
        b.setBackground(bg);
        b.setPadding(dp(16), dp(10), dp(16), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.topMargin = dp(10);
        root.addView(b, lp);
        b.setOnClickListener(l);
    }

    private void applyRecents() {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am == null) return;
            boolean hide = sp.getBoolean("hide_recents", true);
            for (android.app.ActivityManager.AppTask t : am.getAppTasks()) {
                t.setExcludeFromRecents(hide);
            }
        } catch (Throwable ignored) {}
    }

    private void addSwitch(LinearLayout root, String text, final String key, boolean def) {
        Switch sw = new Switch(this);
        sw.setText(text);
        sw.setTextColor(ON_SURFACE);
        sw.setTextSize(14);
        sw.setChecked(sp.getBoolean(key, def));
        tintSwitch(sw);
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean v) {
                sp.edit().putBoolean(key, v).apply();
                if ("hide_recents".equals(key)) applyRecents();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        root.addView(sw, lp);
    }

    private void tintSwitch(Switch sw) {
        sw.setThumbTintList(ColorStateList.valueOf(accent));
        sw.setTrackTintList(ColorStateList.valueOf(
                (0x55 << 24) | (accent & 0xFFFFFF)));
    }

    private void addEdit(LinearLayout root, String label, final String key, String def) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(ON_VARIANT);
        tv.setTextSize(12);
        tv.setPadding(0, dp(10), 0, dp(4));
        root.addView(tv);
        android.widget.EditText et = new android.widget.EditText(this);
        et.setText(sp.getString(key, def));
        et.setTextColor(ON_SURFACE);
        et.setTextSize(14);
        et.setSingleLine(false);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        et.setMinLines(2);
        et.setMaxLines(4);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(surface);
        bg.setCornerRadius(dp(14));
        et.setBackground(bg);
        et.setPadding(dp(14), dp(8), dp(14), dp(8));
        et.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                sp.edit().putString(key, s.toString()).apply();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4);
        root.addView(et, lp);
    }


    private void addItalicMode(LinearLayout root) {
        TextView tv = new TextView(this);
        tv.setText("斜体模式");
        tv.setTextColor(ON_VARIANT);
        tv.setTextSize(12);
        tv.setPadding(0, dp(10), 0, 0);
        root.addView(tv);
        android.widget.RadioGroup g = new android.widget.RadioGroup(this);
        g.setOrientation(android.widget.RadioGroup.HORIZONTAL);
        for (int i = 0; i < 4; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setId(3000 + i);
            rb.setText(i == 0 ? "关闭" : i == 1 ? "全部斜体" : i == 2 ? "仅歌名" : "仅歌手");
            rb.setTextColor(ON_SURFACE);
            rb.setTextSize(13);
            rb.setButtonTintList(ColorStateList.valueOf(accent));
            g.addView(rb);
        }
        g.check(3000 + sp.getInt("italic_mode", 0));
        g.setOnCheckedChangeListener(new android.widget.RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.RadioGroup grp, int id) {
                sp.edit().putInt("italic_mode", id - 3000).apply();
            }
        });
        root.addView(g);
    }

    private void addItalicSwitch(LinearLayout root, String label, final String key) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextColor(ON_SURFACE);
        sw.setTextSize(14);
        sw.setChecked(sp.getBoolean(key, false));
        tintSwitch(sw);
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean v) {
                sp.edit().putBoolean(key, v).apply();
                sendItalic();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        root.addView(sw, lp);
    }

    private void sendItalic() {
        try {
            Intent i = new Intent("com.dsh.lyriccard.ITALIC");
            i.setPackage("com.android.launcher");
            i.putExtra("cur", sp.getBoolean("it_cur", false));
            i.putExtra("other", sp.getBoolean("it_other", false));
            i.putExtra("title", sp.getBoolean("it_title", false));
            i.putExtra("artist", sp.getBoolean("it_artist", false));
            sendBroadcast(i);
        } catch (Throwable ignored) {}
    }

    private void addFontSlider(LinearLayout root) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, 0);
        TextView tv = new TextView(this);
        tv.setText("字体大小");
        tv.setTextColor(ON_VARIANT);
        tv.setTextSize(12);
        row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1));
        final TextView value = new TextView(this);
        value.setText(sp.getInt("font_scale", 100) + "%");
        value.setTextColor(accent);
        value.setTextSize(12);
        row.addView(value);
        root.addView(row);
        final android.widget.SeekBar bar = new android.widget.SeekBar(this);
        if (Build.VERSION.SDK_INT >= 26) bar.setMin(70);
        bar.setMax(160);
        bar.setProgress(sp.getInt("font_scale", 100));
        bar.setProgressTintList(ColorStateList.valueOf(accent));
        bar.setThumbTintList(ColorStateList.valueOf(accent));
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar b, int v, boolean fromUser) {
                sp.edit().putInt("font_scale", v).apply();
                value.setText(v + "%");
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar b) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar b) {}
        });
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        addButton(root, "恢复默认字体大小（100%）", new View.OnClickListener() {
            @Override public void onClick(View v) {
                sp.edit().putInt("font_scale", 100).apply();
                bar.setProgress(100);
            }
        });
    }

    private void addSlider(LinearLayout root, String label, final String key, int max, int def) {
        final String unit = key.startsWith("pad_") ? "dp" : "";
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, 0);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(ON_VARIANT);
        tv.setTextSize(12);
        row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1));
        final TextView value = new TextView(this);
        value.setText(sp.getInt(key, def) + unit);
        value.setTextColor(accent);
        value.setTextSize(12);
        row.addView(value);
        root.addView(row);
        android.widget.SeekBar bar = new android.widget.SeekBar(this);
        bar.setMax(max);
        bar.setProgress(sp.getInt(key, def));
        bar.setProgressTintList(ColorStateList.valueOf(accent));
        bar.setThumbTintList(ColorStateList.valueOf(accent));
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar b, int v, boolean fromUser) {
                sp.edit().putInt(key, v).apply();
                value.setText(v + unit);
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar b) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar b) {}
        });
        sliders.put(key, bar);
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));
    }

    private void restartLauncher() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "am force-stop com.android.launcher"});
            p.waitFor();
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
            android.widget.Toast.makeText(this, "已重启桌面", android.widget.Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            try {
                Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:com.android.launcher"));
                startActivity(info);
            } catch (Throwable ignored) {}
            android.widget.Toast.makeText(this, "无 root 权限，请在应用信息页强行停止",
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void requestPin() {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(this);
            if (Build.VERSION.SDK_INT >= 26 && mgr.isRequestPinAppWidgetSupported()) {
                mgr.requestPinAppWidget(new ComponentName(this, LyricWidgetProvider.class), null, null);
                return;
            }
        } catch (Exception ignored) {}
        android.widget.Toast.makeText(this, "请长按桌面 → 小组件 → LyricCard",
                android.widget.Toast.LENGTH_LONG).show();
    }

    private int readSystemAccent() {
        int[] ids = {
                android.R.color.system_accent1_200,
                android.R.color.system_accent1_400,
                android.R.color.system_accent1_100,
                android.R.color.system_accent1_500
        };
        for (int id : ids) {
            try {
                int c = getColor(id);
                if ((c >>> 24) != 0) return c;
            } catch (Throwable ignored) {}
        }
        return 0xFFFFD68A;
    }

    private boolean hasNotificationAccess() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName() + "/" + MediaListener.class.getName());
    }

    private void updateStatus() {
        boolean ok = hasNotificationAccess();
        status.setText(ok ? "已授权，正在监听媒体会话" : "未授权：请点“授予通知使用权”");
        status.setTextColor(ok ? accent : 0xFFFFC46B);
    }
}
