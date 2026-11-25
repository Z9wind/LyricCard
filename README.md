# LyricCard

ColorOS 桌面歌词小组件（AppWidget），读取播放器 MediaSession 的 `lyricInfo` 显示逐行歌词。

## 演示

![演示](docs/demo.gif)

也可以下载完整视频：[demo.mp4](docs/demo.mp4)

## 功能

- 桌面小组件显示当前播放音乐歌词（目前适配网易云音乐、Qplayer、Salt player）
- 颜色跟随封面取色（36 色相布局）或 Monet 主题色
- 字号、四周边距、专辑取色 or 莫奈取色发光、深色/浅色背景、对齐可调
- 字体斜体样式：全部 / 仅歌名 / 仅歌手
- 快捷设置磁贴保活
- 最近任务隐藏
- 可选 LSPosed 模块：桌面可见性广播，用于省电

注：样式参考 ColorOS-Live-Lyrics-Bridge（Apache-2.0），详见 NOTICE。

## 歌词来源与 Provider（重要）

本应用只读取播放器 MediaSession 里的 `lyricInfo` 字段，**它不自己写歌词**。多数国内播放器不会原生写入这个字段，需要一个「歌词 Provider / 插件」把歌词桥接进去：

- **推荐**：[LyricProvider](https://github.com/tomakino/LyricProvider) — 按你的播放器（网易云、QQ 音乐、Salt Player 等）下载对应插件并启用。
- 或者使用 [ColorOS-Live-Lyrics-Bridge](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge) 的 Provider（LSPosed 模块，需要 Root）。
- **未 Root 的用户**：打开应用里的「未root无provider兼容模式」，会通过在线歌词源（网易云 / QQ 音乐 / lrclib）匹配歌词，可能有轻微延迟。

> 没有 Provider 又没开兼容模式时，卡片会显示「暂无播放」。

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

## License

Apache License 2.0，见 [LICENSE](LICENSE)。Copyright 2026 LyricCard contributors。
