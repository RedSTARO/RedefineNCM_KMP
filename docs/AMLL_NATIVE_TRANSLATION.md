# AMLL 原生 Compose 移植来源映射（宿主侧）

全屏播放页只有一条渲染路径：原生 Compose。Legacy WebView 渲染器、`player.html`
宿主页、`amllAssets/` 打包资产和 `androidApp/amll-builder/` Node 构建器已于
2026-09-06 删除。

歌词引擎本身（解析、优化器、弹簧、时间线、布局、字级遮罩与强调、分词、减少动态效果、
封面背景）已抽出到独立仓库 **AMLL_Jetpack_Compose**（本仓库的同名 git 子模块；Gradle 坐标
`com.leejlredstar.amll:amll-compose`）。**逐符号的 TypeScript / CSS → Kotlin 映射表、
以及有意保留的平台 API 差异清单，以该仓库的 `docs/AMLL_NATIVE_TRANSLATION.md`
为准**，本文件不再重复。

本文件只记录仍留在本仓库的宿主侧部分。

## 固定的来源快照

### AMLL npm 包

| 包 | 版本 | 许可证 |
|---|---:|---|
| `@applemusic-like-lyrics/core` | `0.5.2` | `AGPL-3.0-only` |
| `@applemusic-like-lyrics/lyric` | `1.0.2` | `AGPL-3.0-only` |
| `@applemusic-like-lyrics/ttml` | `1.0.1` | `AGPL-3.0-only` |

上游仓库：<https://github.com/amll-dev/applemusic-like-lyrics>

### RedefineNCM AMLL WebView 宿主

被原生实现取代的 HTML/JS 宿主基准固定在 Git 提交
`d3cfaf168d5f605d5bcea265055d84fedf691e6d`：

| 文件 | Git blob |
|---|---|
| `shared/src/commonMain/amllAssets/amll/player.html` | `1aa535041c73522f8b5e435ca1a6854ade91a253` |
| `shared/src/commonMain/amllAssets/amll/style.css` | `427b30db03b20fa596a8683bd3935e86c092948e` |
| `androidApp/amll-builder/entry.js` | `16a704321d594f6e1de5989431c4d98a15c2fe49` |

这些文件已从工作区删除；审计时用
`git show d3cfaf168d5f605d5bcea265055d84fedf691e6d:<path>` 恢复对应内容。

## 宿主页面到本仓库 Compose 的映射

| 旧宿主来源 | Kotlin 位置 | 映射说明 |
|---|---|---|
| `player.html`：`#desktop-back`、`#wiki-info` | `ui/amll/NativeAmllScreen.kt`：`AmllTopActions`；`SongWikiDetailsButton` | 返回和歌曲详情入口直接进入同一公共 Compose 树。 |
| `player.html`：`#wiki-overlay`、`#wiki-dialog`、`.wiki-*` selector 与 `renderSongWiki` | `ui/component/SongWikiDetails.kt`：`SongWikiDetailsSheet`、`WikiDialogContent`、`WikiHero`、`WikiMetadata`、`WikiStatePanel`、`WikiSectionCard` | 颜色 token、桌面/移动/低高度断点、加载/空/错误/内容状态、焦点与关闭交互；`.wiki-hero` 的 `linear-gradient(135deg, …)` 使用 CSS magic-corner 投影轴，而非按盒子对角线近似。 |
| `player.html`：`setSongDetails`、`setSongWiki*`、media-id guard | `NativeAmllScreen` 的 `SongWikiUiState.scopedTo` 及 `NowPlayingViewModel` 的歌曲详情状态 | 不再经过 JavaScript bridge；仍以当前媒体 ID 丢弃过期结果，切歌关闭详情。 |
| `player.html`：`playWikiDynamicCover()`、`pauseWikiDynamicCover()` | `NativeAmllScreen`：`shouldPlayAmllDynamicBackground` | 正常动效下全屏视频持续播放到详情视频呈现首帧为止；只有减少动态效果会立即暂停。 |
| `player.html`：`#dynamic-bg` 的视频元素 | `ui/component/NativeDynamicCoverLayer.kt` 及各平台 actual | 库的 `AmllBackground` 通过 `AmllDynamicCoverLayer` 槽位取帧；http(s) 判定、overscan、模糊、缩放、亮度和遮罩仍由库计算。Android 用 Media3 + `TextureView`，iOS 用 `AVPlayerLayer` / `UIKitView`，Desktop 用 FFmpeg 解码为 `ImageBitmap`，Web 用 `HTMLVideoElement`。 |
| `player.html`：`#desktop-console` 与全部 `console-*` 元素 | `ui/component/AutoHideMiniPlayerController.kt` | 桌面端曾把播放控制做进 WebView 页内，因为 Compose 层无法盖住 WebView2 子 HWND。现在四端统一使用 Android 的控制岛屿：标题/艺人、进度与拖动、上一首/播放暂停/下一首、收藏、随机、队列、评论，另有折叠态与歌词详情展开；桌面端展开态另有独占的输出音量滑块（手机端音量由硬件键与系统面板负责）。点击歌词行只跳转，不展开岛屿；点击行间空白或按键才展开。自动收起延时四端统一为 `3600 ms`（Android 基准），桌面端 30 秒特例已随页内控制台一同删除。 |
| `entry.js`：`LyricPlayer` 初始化、`setCurrentTime`、`setPlaying` | `NativeAmllScreen` 与库的 `AmllPlayerEngine` | Compose 生命周期和 `withFrameNanos` 替代脚本加载、`requestAnimationFrame` 与 bridge 初始化。 |
| `player.html`：`loadEngine`、`signalReady`、`showError`、`setStatus`、`#loading` | `lyric/LyricStateOverlay.kt` | bundle 加载状态不再存在；剩下的加载 / 空 / 错误 / 重试状态由公共 Compose overlay 呈现。 |
| `player.html`：`applyViewportSize()` | `NativeAmllScreen` 的 `BoxWithConstraints` | DOM 显式尺寸写入由 Compose 约束替代。 |
| `player.html`：`@JavascriptInterface`、WebView2 bind、`AmllPage` 全局对象 | — | 宿主通信层整体删除，由 state flow、Compose 状态和直接 Kotlin 回调替代。 |

## 许可证

本仓库中由 AMLL 翻译或改编的 Kotlin 文件使用
`SPDX-License-Identifier: AGPL-3.0-only` 标记。版权与修改说明见
[`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md)，完整许可证文本见
[`LICENSES/AGPL-3.0-only.txt`](../LICENSES/AGPL-3.0-only.txt)。Desktop 发布包会把
完整 [`LICENSES/`](../LICENSES/) 目录与该 notice 一并写入应用资源。
