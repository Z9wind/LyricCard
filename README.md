# LyricCard

ColorOS 桌面歌词小组件（AppWidget），读取播放器 MediaSession 的 `lyricInfo` 显示逐行歌词。

>  本项目由 AI 辅助制作

## 演示

![演示](docs/demo.gif)

![桌面效果](docs/screenshot.jpg)

> 预览桌面壁纸来源于抖音 @芋泥啵啵香奶茶https://v.douyin.com/wSb4yklGk0w/

## 功能

- 桌面小组件显示当前播放音乐歌词（目前适配网易云音乐、Qplayer、Salt player）

注：样式参考 ColorOS-Live-Lyrics-Bridge（Apache-2.0），详见 NOTICE。

## 歌词来源与 Provider（重要）

本应用只读取播放器 MediaSession 里的 `lyricInfo` 字段，多数国内播放器不会原生写入这个字段，需要一个「歌词 Provider / 插件」(该插件需lsp）把歌词桥接进去：

- **推荐**：[LyricProvider](https://github.com/tomakino/LyricProvider) — 按你的播放器（网易云、QQ 音乐、Salt Player 等）下载对应插件并启用。
- 或者使用 [ColorOS-Live-Lyrics-Bridge](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge) 的 Provider（LSPosed 模块，需要 Root）。
- **未 Root 的用户**：打开应用里的「未root无provider兼容模式」，会通过在线歌词源（网易云 / QQ 音乐 / lrclib）匹配歌词，可能有轻微延迟。

## 更多歌词来源（2.51+）

除了播放器写入的 `lyricInfo`，新版还会按以下顺序寻找歌词：

1. **词幕（Lyricon）订阅桥**：设备上装有词幕系 Provider 时，应用会按 Lyricon 的公共订阅协议向 SystemUI 里的中心服务订阅，直接拿到 Provider 的完整时间轴歌词，不需要联网。原版词幕与后续 fork 只要在 SystemUI 注册了中心服务都能接上，不绑定具体分支；多个词幕同时存在时保留先建立的稳定连接。
2. **标题里的当前行**：部分 Provider 不写 `lyricInfo`，而是把当前歌词行写进媒体标题，并把歌手字段改成「歌曲名 - 歌手」。应用会自动还原歌名/歌手，并把这一行作为单行歌词显示。
3. **在线匹配**：以上来源都没有时，仍按「未root无provider兼容模式」用歌名/歌手匹配在线歌词。

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

- 请遵守上述参考项目各自的许可证。

## 省电与后台（2.75+）

- 离开桌面（切到其他应用或熄屏）时暂停读取播放器和更新小组件，回到桌面立即恢复刷新；
- 前台服务声明 `mediaPlayback` 类型并新增对应权限，适配 Android 14+ 的前台服务要求；
- 不再写 `dbg.log` 日志文件，减少存储和内存占用；
- Xposed 模块去掉了静态作用域，安装后在 LSPosed 里手动勾选「桌面」即可；
- 小组件实例失效（例如重装应用后）会导致更新被系统静默丢弃、歌词停住，删除桌面卡片重新添加即可恢复。

## License

Apache License 2.0，见 [LICENSE](LICENSE)。Copyright 2026 LyricCard contributors。
