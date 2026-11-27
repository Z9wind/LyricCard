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
    private Button keepAliveBtn;
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
        updateKeepAliveLabel();
        autoCheckUpdate();
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
        keepAliveBtn = addButton(quick, "无感保活", new View.OnClickListener() {
            @Override public void onClick(View v) { showKeepAliveDialog(); }
        });
        updateKeepAliveLabel();
        addButton(quick, "授予通知使用权", new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
                catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            }
        });
        addButton(quick, "添加桌面卡片", new View.OnClickListener() {
            @Override public void onClick(View v) { requestPin(); }
        });
        addButton(quick, "授予使用情况访问", new View.OnClickListener() {
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

        if (!sp.contains("color_mode")) {
            sp.edit().putInt("color_mode", sp.getBoolean("monet", false) ? 2 : 1).apply();
        }
        int mode0 = sp.getInt("color_mode", 1);
        if (mode0 == 2) { // 旧的 ColorOS 自读色已移除，回退到跟随封面
            mode0 = 1;
            sp.edit().putInt("color_mode", 1).apply();
        }

        addColorSwatch(colors, "系统默认色", readSystemAccent());
        addColorSwatch(colors, "当前封面取色", sp.getInt("cover_color", 0xFFFFD68A));

        final Switch swDefault = new Switch(this);
        swDefault.setText("使用系统默认色");
        final Switch swCover = new Switch(this);
        swCover.setText("跟随封面取色");
        final boolean[] updating = {false};
        final android.widget.CompoundButton.OnCheckedChangeListener modeListener =
                new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean v) {
                if (updating[0]) return;
                boolean any = swDefault.isChecked() || swCover.isChecked();
                if (!v && !any) {
                    updating[0] = true;
                    b.setChecked(true);
                    updating[0] = false;
                    return;
                }
                if (!v) return;
                updating[0] = true;
                swDefault.setChecked(b == swDefault);
                swCover.setChecked(b == swCover);
                sp.edit().putInt("color_mode", b == swDefault ? 0 : 1).apply();
                updating[0] = false;
                LyricService.start(MainActivity.this);
            }
        };
        for (Switch sw : new Switch[]{swDefault, swCover}) {
            sw.setTextColor(ON_SURFACE);
            sw.setTextSize(14);
            tintSwitch(sw);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(6);
            colors.addView(sw, lp);
            sw.setOnCheckedChangeListener(modeListener);
        }
        updating[0] = true;
        swDefault.setChecked(mode0 == 0);
        swCover.setChecked(mode0 == 1);
        updating[0] = false;

        groupTitle(root, "外观");
        LinearLayout look = card(root);
        addSwitch(look, "当前行发光", "glow", true);
        addSwitch(look, "背景底色切换", "bg", true);
        addSwitch(look, "无歌词时在线匹配", "online", false);
        addSwitch2(look, "未root无provider兼容模式", "（在线匹配歌词，可能存在轻微延迟）", "compat", false);
        addSwitch2(look, "后台持续更新（前台服务）", "会在通知栏添加一条静默通知，防止系统冻结歌词服务", "foreground", true);
        addSwitch(look, "离开桌面暂停刷新（Root）", "pause_root", false);
        addSwitch(look, "离开桌面暂停刷新（未root）", "pause_usage", false);
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

        groupTitle(root, "文字样式");
        LinearLayout glowCard = card(root);
        addSlider(glowCard, "歌名透明度", "title_alpha", 100, 100);
        addSlider(glowCard, "歌手名透明度", "artist_alpha", 100, 100);

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

        LinearLayout restartCard = card(root);
        addButton(restartCard, "重启桌面（需 root）", new View.OnClickListener() {
            @Override public void onClick(View v) { restartLauncher(); }
        });

        TextView tip = new TextView(this);
        tip.setText("卡片大小在桌面上拖动边缘调整；修改外观后点“重启桌面”生效。");
        tip.setTextColor(ON_VARIANT);
        tip.setTextSize(12);
        tip.setLineSpacing(dp(3), 1f);
        tip.setPadding(dp(4), dp(18), dp(4), 0);
        root.addView(tip);

        homePage = scroll;
        settingsPage = buildAppPage();
        contentBox = new android.widget.FrameLayout(this);
        contentBox.addView(homePage);
        contentBox.addView(settingsPage);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(surface);
        shell.addView(contentBox, new LinearLayout.LayoutParams(-1, 0, 1));
        shell.addView(buildBottomBar(), new LinearLayout.LayoutParams(-1, dp(54)));
        setContentView(shell);
        showTab(0);
        applySystemBars();
    }

    private View homePage, settingsPage;
    private android.widget.FrameLayout contentBox;
    private TextView tabHome, tabSettings;

    private ScrollView buildAppPage() {
        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(surface);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        sc.addView(root);

        TextView title = new TextView(this);
        title.setText("关于");
        title.setTextColor(ON_SURFACE);
        title.setTextSize(26);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        LinearLayout info = card(root);
        TextView v = new TextView(this);
        v.setText("LyricCard  版本 " + versionName());
        v.setTextColor(ON_SURFACE);
        v.setTextSize(14);
        info.addView(v);
        TextView repo = new TextView(this);
        repo.setText("GitHub：" + UpdateChecker.repoUrl());
        repo.setTextColor(accent);
        repo.setTextSize(13);
        repo.setPadding(0, dp(6), 0, 0);
        repo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse(UpdateChecker.repoUrl())));
                } catch (Throwable ignored) {}
            }
        });
        info.addView(repo);

        LinearLayout actions = card(root);
        addButton(actions, "检查更新", new View.OnClickListener() {
            @Override public void onClick(View view) { checkUpdate(true); }
        });
        addButton(actions, "来源", new View.OnClickListener() {
            @Override public void onClick(View view) { showAbout(); }
        });

        return sc;
    }

    private LinearLayout buildBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(container);
        tabHome = tabItem("主页");
        tabSettings = tabItem("关于");
        bar.addView(tabHome, new LinearLayout.LayoutParams(0, -1, 1));
        bar.addView(tabSettings, new LinearLayout.LayoutParams(0, -1, 1));
        tabHome.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(0); }
        });
        tabSettings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(1); }
        });
        return bar;
    }

    private TextView tabItem(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setGravity(android.view.Gravity.CENTER);
        t.setTextColor(ON_VARIANT);
        return t;
    }

    private void showTab(int i) {
        if (homePage == null) return;
        homePage.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
        settingsPage.setVisibility(i == 1 ? View.VISIBLE : View.GONE);
        tabHome.setTextColor(i == 0 ? accent : ON_VARIANT);
        tabSettings.setTextColor(i == 1 ? accent : ON_VARIANT);
    }

    /** 自动检查更新：默认开启，最多 24 小时一次。 */
    private void autoCheckUpdate() {
        try {
            if (!sp.getBoolean("auto_update", true)) return;
            long last = sp.getLong("last_update_check", 0);
            if (System.currentTimeMillis() - last < 24L * 3600L * 1000L) return;
            checkUpdate(false);
        } catch (Throwable ignored) {}
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "?";
        }
    }

    private void checkUpdate(final boolean manual) {
        if (manual) android.widget.Toast.makeText(this, "正在检查更新…", android.widget.Toast.LENGTH_SHORT).show();
        sp.edit().putLong("last_update_check", System.currentTimeMillis()).apply();
        UpdateChecker.check(this, new UpdateChecker.Callback() {
            @Override public void onResult(boolean ok, String version, final String url, String notes, String error) {
                if (!ok) {
                    if (manual) android.widget.Toast.makeText(MainActivity.this,
                            "检查失败：" + error, android.widget.Toast.LENGTH_LONG).show();
                    return;
                }
                String remote = version.startsWith("v") ? version.substring(1) : version;
                if (remote.equals(versionName())) {
                    if (manual) android.widget.Toast.makeText(MainActivity.this,
                            "已是最新版本 " + versionName(), android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                String msg = "发现新版本 " + version + "\n当前版本 " + versionName()
                        + (notes == null || notes.isEmpty() ? "" : "\n\n" + notes);
                new android.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("检查更新")
                        .setMessage(msg)
                        .setPositiveButton("去下载", new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(android.content.DialogInterface d, int w) {
                                try {
                                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
                                } catch (Throwable ignored) {}
                            }
                        })
                        .setNegativeButton("关闭", null)
                        .show();
            }
        });
    }

    private void showAbout() {
        String msg = "版本 " + versionName() + "\n\n"
                + "开源仓库：\n" + UpdateChecker.repoUrl() + "\n\n"
                + "致谢（思路与样式参考）：\n"
                + "· ColorOS-Live-Lyrics-Bridge\n  https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge\n"
                + "· lyricon\n  https://github.com/Kifranei/lyricon\n"
                + "· GKD\n  https://github.com/gkd-kit/gkd\n"
                + "· LyricProvider\n  https://github.com/tomakino/LyricProvider";
        new android.app.AlertDialog.Builder(this)
                .setTitle("来源")
                .setMessage(msg)
                .setPositiveButton("打开仓库", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                    android.net.Uri.parse(UpdateChecker.repoUrl())));
                        } catch (Throwable ignored) {}
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    /** 让状态栏/导航栏颜色跟随页面底色，并按明暗自动切换图标颜色。 */
    private void applySystemBars() {
        try {
            android.view.Window w = getWindow();
            w.setStatusBarColor(surface);
            w.setNavigationBarColor(surface);
            double lum = 0.2126 * Color.red(surface) + 0.7152 * Color.green(surface) + 0.0722 * Color.blue(surface);
            boolean darkBg = lum < 128;
            if (Build.VERSION.SDK_INT >= 30) {
                android.view.WindowInsetsController c = w.getInsetsController();
                if (c != null) {
                    int mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    c.setSystemBarsAppearance(darkBg ? 0 : mask, mask);
                }
            } else {
                android.view.View decor = w.getDecorView();
                int flags = decor.getSystemUiVisibility();
                if (darkBg) flags &= ~android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                else flags |= android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                decor.setSystemUiVisibility(flags);
            }
        } catch (Throwable ignored) {}
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

    /** 依据无障碍开关和磁贴开关更新顶部按钮文案。 */
    private void updateKeepAliveLabel() {
        if (keepAliveBtn == null) return;
        boolean a11y = isA11yEnabled();
        boolean tile = sp.getBoolean("keep_alive", true);
        if (a11y && tile) {
            keepAliveBtn.setText("无感保活（已开启）");
        } else {
            String missing = !a11y && !tile ? "无障碍、磁贴" : (!a11y ? "无障碍" : "磁贴");
            keepAliveBtn.setText("无感保活（未开启：缺" + missing + "）");
        }
    }

    /** 无障碍服务是否已在系统设置里启用。 */
    private boolean isA11yEnabled() {
        try {
            String enabled = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabled != null && enabled.contains(getPackageName() + "/");
        } catch (Throwable t) {
            return false;
        }
    }

    /** 无感保活引导：无障碍 + 快捷磁贴两步，都不产生常驻通知。 */
    private void showKeepAliveDialog() {
        final boolean a11y = isA11yEnabled();
        final boolean tile = sp.getBoolean("keep_alive", true);
        String msg = "无感保活需要两步，都不会产生常驻通知：\n\n"
                + "1. 开启无障碍" + (a11y ? "（已开启）" : "（未开启）")
                + "\n   系统绑定该服务后，ColorOS 就不会冻结歌词进程。\n\n"
                + "2. 在快捷设置里添加「歌词卡片」磁贴并点亮" + (tile ? "（磁贴开关当前为开）" : "（磁贴开关当前为关）")
                + "\n   磁贴会把进程优先级提到前台级别，并拉起歌词服务。";
        new android.app.AlertDialog.Builder(this)
                .setTitle("无感保活")
                .setMessage(msg)
                .setPositiveButton(a11y ? "添加/开启磁贴" : "去开启无障碍", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        if (!a11y) {
                            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
                            catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                        } else {
                            addQsTile();
                        }
                    }
                })
                .setNeutralButton(a11y ? "无障碍设置" : "添加磁贴", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        if (a11y) {
                            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
                            catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                        } else {
                            addQsTile();
                        }
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    /** 尝试直接请求系统添加快捷设置磁贴（Android 13+），失败则提示手动添加。 */
    private void addQsTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                android.app.StatusBarManager sbm = getSystemService(android.app.StatusBarManager.class);
                sbm.requestAddTileService(new ComponentName(this, LyricTile.class), "歌词卡片",
                        android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_lyric),
                        getMainExecutor(), new java.util.function.Consumer<Integer>() {
                            @Override public void accept(Integer result) {}
                        });
                return;
            } catch (Throwable ignored) {}
        }
        android.widget.Toast.makeText(this, "请下拉通知栏 → 编辑快捷设置 → 添加「歌词卡片」并点亮",
                android.widget.Toast.LENGTH_LONG).show();
    }

    private void addColorSwatch(LinearLayout root, String text, int color) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(ON_VARIANT);
        label.setTextSize(12);
        label.setPadding(0, dp(10), 0, 0);
        root.addView(label);
        View sw = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color | 0xFF000000);
        g.setCornerRadius(dp(12));
        sw.setBackground(g);
        root.addView(sw, new LinearLayout.LayoutParams(-1, dp(30)));
    }


    private Button addButton(LinearLayout root, String text, View.OnClickListener l) {
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
        return b;
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

    private void addSwitch2(LinearLayout root, String main, String sub, final String key, boolean def) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = new TextView(this);
        t1.setText(main);
        t1.setTextColor(ON_SURFACE);
        t1.setTextSize(14);
        TextView t2 = new TextView(this);
        t2.setText(sub);
        t2.setTextColor(ON_VARIANT);
        t2.setTextSize(11);
        t2.setPadding(0, dp(2), 0, 0);
        col.addView(t1);
        col.addView(t2);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        final Switch sw = new Switch(this);
        sw.setChecked(sp.getBoolean(key, def));
        tintSwitch(sw);
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean v) {
                sp.edit().putBoolean(key, v).apply();
            }
        });
        row.addView(sw);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        root.addView(row, lp);
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
                try {
                    android.content.Intent ri = new android.content.Intent("com.dsh.lyriccard.REFRESH");
                    ri.setPackage("com.dsh.lyriccard");
                    sendBroadcast(ri);
                    LyricService.start(MainActivity.this);
                } catch (Throwable ignored) {}
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4);
        root.addView(et, lp);
    }


    private void addItalicMode(LinearLayout root) {
        TextView tv = new TextView(this);
        android.text.SpannableString italicTitle = new android.text.SpannableString("斜体模式（部分设备可能不生效）");
        int noteIdx = italicTitle.toString().indexOf('（');
        if (noteIdx >= 0) {
            italicTitle.setSpan(new android.text.style.ForegroundColorSpan(ON_VARIANT),
                    noteIdx, italicTitle.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tv.setText(italicTitle);
        tv.setTextColor(ON_SURFACE);
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
        row.addView(tv);
        TextView resetFont = new TextView(this);
        resetFont.setText("\u21BA");
        resetFont.setTextColor(ON_VARIANT);
        resetFont.setTextSize(16);
        resetFont.setPadding(dp(6), 0, dp(6), 0);
        row.addView(resetFont);
        View spacer = new View(this);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
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
        resetFont.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                sp.edit().putInt("font_scale", 100).apply();
                bar.setProgress(100);
            }
        });
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar b, int v, boolean fromUser) {
                sp.edit().putInt("font_scale", v).apply();
                value.setText(v + "%");
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar b) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar b) {}
        });
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));
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
        row.addView(tv);
        TextView reset = new TextView(this);
        reset.setText("\u21BA");
        reset.setTextColor(ON_VARIANT);
        reset.setTextSize(16);
        reset.setPadding(dp(6), 0, dp(6), 0);
        row.addView(reset);
        View spacer = new View(this);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        final TextView value = new TextView(this);
        value.setText(sp.getInt(key, def) + unit);
        value.setTextColor(accent);
        value.setTextSize(12);
        row.addView(value);
        root.addView(row);
        final android.widget.SeekBar bar = new android.widget.SeekBar(this);
        bar.setMax(max);
        bar.setProgress(sp.getInt(key, def));
        bar.setProgressTintList(ColorStateList.valueOf(accent));
        bar.setThumbTintList(ColorStateList.valueOf(accent));
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                sp.edit().putInt(key, def).apply();
                bar.setProgress(def);
            }
        });
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
            int code = p.waitFor();
            if (code != 0) throw new RuntimeException("su denied: " + code);
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
