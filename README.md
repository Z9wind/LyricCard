# LyricCard

ColorOS 桌面歌词小组件（AppWidget），读取播放器 MediaSession 的 `lyricInfo` 显示逐行歌词。

>  本项目由 AI 辅助制作

## 演示

![演示](docs/demo.gif)

> 预览桌面壁纸来源于抖音 @芋泥啵啵香奶茶

## 功能

- 桌面小组件显示当前播放音乐歌词（目前适配网易云音乐、Qplayer、Salt player）

注：样式参考 ColorOS-Live-Lyrics-Bridge（Apache-2.0），详见 NOTICE。

## 歌词来源与 Provider（重要）

本应用只读取播放器 MediaSession 里的 `lyricInfo` 字段，多数国内播放器不会原生写入这个字段，需要一个「歌词 Provider / 插件」(该插件需lsp）把歌词桥接进去：

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
  本项目借鉴其「从 MediaMetadata 的 lyricInfo 读取歌词 / 双行对照」的思路与卡片样式，代码为独立实现。
- 状态栏歌词、斜体（合成倾斜）做法参考
  [lyricon](https://github.com/Kifranei/lyricon)。
- 磁贴保活与最近任务隐藏的写法参考
  [GKD](https://github.com/gkd-kit/gkd)。

## 说明

- AppWidget 受 RemoteViews 限制：字体文件、运行时斜体等无法动态设置，本项目的斜体通过预生成布局实现。
- 请遵守上述参考项目各自的许可证。

## License

Apache License 2.0，见 [LICENSE](LICENSE)。Copyright 2026 LyricCard contributors。
