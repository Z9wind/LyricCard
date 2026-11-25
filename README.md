# LyricCard

ColorOS 桌面歌词小组件（AppWidget），读取播放器 MediaSession 的 `lyricInfo` 显示逐行歌词。

## 功能

- 桌面小组件显示当前歌词、历史/未来歌词、双语翻译
- 颜色跟随封面取色（36 色相布局）与 Monet 主题色
- 字号、四周边距、发光开关、深色/浅色背景、对齐方式可调
- 斜体模式（全部 / 仅歌名 / 仅歌手）
- 紧凑模式（1×N 等矮卡片只显示当前歌词）
- LSPosed 模块（可选）：只做桌面可见性广播，用于离开桌面时暂停刷新省电
- 快捷设置磁贴：开/关歌词服务
- 最近任务隐藏

## 构建

```bash
./build.sh
```

需要 aapt2、d8、apksigner（Android build-tools）与一个可用的 keystore（`build/debug.keystore`）。

## 来源与致谢

- 桌面歌词卡片的视觉样式、`lyricInfo` 解析思路参考
  [ColorOS-Live-Lyrics-Bridge](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge)。
  本项目只借鉴其「从 MediaMetadata 的 lyricInfo 读取歌词 / 双行对照」的思路与卡片样式，代码为独立实现。
- 状态栏歌词、斜体（合成倾斜）做法参考
  [lyricon](https://github.com/Kifranei/lyricon)。
- 磁贴保活与最近任务隐藏的写法参考
  [GKD](https://github.com/gkd-kit/gkd)。

## 说明

- AppWidget 受 RemoteViews 限制：字体文件、运行时斜体等无法动态设置，本项目的斜体通过预生成布局实现。
- 请遵守上述参考项目各自的许可证。
