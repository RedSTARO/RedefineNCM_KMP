# RedefineNCM → KMP 迁移进度

> 最后更新：2026-09-06　｜　状态：**四目标均已启用；全屏播放器为单一路由、单一原生 Compose 渲染器**。项目内已无任何 WebView；歌词引擎是子模块 `AMLL_Jetpack_Compose/`（独立仓库），以 Gradle composite build 接入。
> 权威细节与"目标规范 vs 现状差异清单"见 `AGENTS.md`（本仓库）与 `../RedefineNCM/AGENTS.md`（原始仓库，已冻结）。
> 本文件只做"全步骤 / 已完成 / 剩余"的进度总览。

---

## 0. 构建验证状态（Android + Desktop + Web + iOS 均已在真实工具链上构建）

| 任务 | 结果 |
|---|---|
| `:shared:compileKotlinJvm` + `:desktopApp:compileKotlin` | ✅ 本分支通过（2026-07-27） |
| `:shared:compileAndroidMain` + `:androidApp:assembleDebug`（完整 APK） | ✅ 本分支通过（2026-07-27） |
| `:shared:jvmTest`（含 **PlayQueueTest**、**AmllLyricModelTest**） | ✅ 本分支通过（2026-07-27） |
| `:shared:compileKotlinIosSimulatorArm64` | ✅ 通过（2026-07-27，Windows）|
| `:shared:linkDebugFrameworkIosSimulatorArm64` | ✅ **首次通过（2026-08-21，macOS 26 / Xcode 26.5）**；此前一直崩在 Kotlin/Native codegen |
| `:shared:iosSimulatorArm64Test` | ✅ **首次真正运行（2026-08-21）**：374 tests / 0 failures |
| `xcodebuild -sdk iphoneos`（真机签名构建 + 安装） | ✅ **首次通过（2026-08-21）**：iPad 10 / iPadOS 17.5.1 安装成功 |
| `:shared:compileKotlinWasmJs` | ✅ 本分支通过（2026-07-27） |
| `:shared:wasmJsBrowserTest` + `:shared:wasmJsBrowserDistribution` | ✅ 本分支通过（2026-07-27，`CHROME_BIN` 使用本机 Edge Chromium）；生产目录为 `shared/build/dist/wasmJs/productionExecutable/` |

> 非 iOS 各行是 2026-07-27 AMLL 分支在 Windows 上的验证结果。iOS 各行于 2026-08-21
> 在 macOS 26 / Xcode 26.5 上补齐——**Kotlin 源码编译通过并不代表 framework 能链接**：
> `object : NSObject()` 这类只在 codegen 阶段才暴露的错误，必须有 macOS 才能发现。

**工具链**：Kotlin 2.4.0 ・ AGP 9.0.1 ・ Gradle 9.1.0 ・ CMP 1.11.1 ・ JB material3 1.11.0-alpha07 ・
Ktor 3.5.0 ・ Koin 4.2.1 ・ Coil 3.5.0（含 coil-network-ktor3）・ SQLDelight 2.3.2 ・
JavaCV 1.5.13 / FFmpeg 8.0.1-1.5.13（Desktop 动态封面，仅打包宿主 native classifier）・
media3 1.10.1 ・ androidx.palette 1.0.0 ・ compileSdk/targetSdk 36 / minSdk 24。

---

## 1. 与原版（../RedefineNCM，已冻结于 2026-06-12）的功能对照

原版最后一笔提交为 "chore: final commit — project migrated to KMP"。以下按原版功能清单核对（2026-07-04 逐文件审计后补齐）：

| 原版功能 | KMP 状态 |
|---|---|
| 三 Tab 导航（推荐/我的/设置）+ 宽屏 NavigationRail | ✅（App.kt，BoxWithConstraints ≥600dp 切 Rail） |
| Splash 路由：无 cookie → Login | ✅（App 启动时 cookie 为空初始 push Login） |
| 启动更新检查（checkUpdate 设置 + GitHub releases） | ✅（MainViewModel.checkAppUpdate → Snackbar；各端使用 Git 派生 BuildInfo，按基础 tag 比较） |
| 搜索共享元素动画（药丸→搜索栏 sharedBounds） | ✅（HomeScreen 内嵌 SharedTransitionLayout + AnimatedVisibility） |
| 搜索联想（设置门控 + 300ms 防抖） | ✅ |
| 每日推荐 / 推荐歌单（含"私人雷达"卡片特例） | ✅ |
| 用户页（模糊 hero + 头像 + 特殊歌单徽章） | ✅（额外支持未登录态 + 登录入口） |
| 歌单详情（封面取色 hero + 播放全部 + 下载全部 + 下载状态指示） | ✅ |
| replacePlaylist 设置（单曲点击：单曲队列 vs 整单队列） | ✅ |
| playlistUpdatePlaycount 上报 | ✅ |
| 全屏播放器（单一路由、原生 Compose） | ✅（四端同一份 `NativeAmllScreen`；无渲染器选项） |
| 随机播放不变量（播放顺序队列 + 高亮同源重建） | ✅（ExoPlayer 按 timeline 播放顺序重建 + skipToIndex 映射窗口索引；VM 实时订阅） |
| 播放状态持久化（队列/索引/进度/shuffle，onPause 存、启动恢复不自动播） | ✅（PlayerStatus.sq + PlatformPlayer.restoreQueue） |
| 歌单批量下载（5 首/批，跳过已存在） | ✅（common 编排；Android MediaStore/JVM 文件系统/iOS NSURLSession/Web OPFS） |
| 离线播放（本地已下载文件优先） | ✅（此前已完成） |
| Room 缓存（cache-then-network） | ✅ SQLDelight 9 表（含 CommentMusic、PlayerStatus） |
| QR 登录 + Cookie 登录 | ✅（修复了二维码 bitmap 从未赋值导致不显示的 bug） |
| UID 解析（缓存 → /user/account 兜底） | ✅（修复原 stub 不调 API 的问题；另支持换号后 refreshAccount） |
| 设置页（server 检查按钮 + 全部开关 + 备份导入导出） | ✅（旧 adaptOriginalAndroidLyric 值现控制 Android 额外 Live Update / Desktop 桌面歌词，默认关闭并即时生效） |
| 迷你播放条（图取色容器色 + 亮度自适应内容色 + spring 动画 + 跑马灯） | ✅ |
| 封面取色（Palette muted→vibrant→dominant） | ✅ Android=Palette；JVM/Web=采样量化近似；iOS=stub |
| 实况通知歌词（歌词作标题 + Android 16 Live Update + 媒体按钮） | ✅（Web 始终有页内歌词层和标题；已授权时同步系统通知） |
| Web 播放与系统媒体控制 | ✅（HTMLAudioElement + Media Session；隐藏/退出页面立即暂停） |
| HiddenTestActivity / serverMocker | ⏸ 有意不迁移（开发调试用途） |
| dailysignin API | 声明未调用（原版同样未调用，保持一致） |

## 2. 剩余步骤

### A. 本环境可做
- [x] **JVM 桌面后端完善**（2026-07-04）：JvmMediaPlayer 补 MP3→PCM_SIGNED 解码转换
      （原实现直接把 MPEG 格式喂 SourceDataLine 会开线失败）+ 按 PCM 字节率 skip 实现
      seek（VBR 近似）；SongDownloader.jvm 真实下载到 ~/Downloads/RedefineNCM/
      （.part 原子改名）；CoilImageTheme.jvm 升级为 Palette 式量化取色
      （RGB555 直方图 + vibrant/muted 目标参数，与 Android Palette 语义一致）。
- [x] **materialIconsExtended → Material Symbols**（2026-07-04）：全部 22 个图标改为
      `ui/icon/AppIcons.kt` 自绘 ImageVector（Material Symbols outlined 24px fill=1，
      path 数据取自 google/material-design-icons），移除 compose.materialIconsExtended 依赖。
- [x] **Web / wasmJs 行为对齐**（2026-07-11）：声明 browser executable target；复用全部
      common Compose 页面、Repository、播放队列和下载编排；补齐真实浏览器播放器、Media
      Session、浏览器历史返回、localStorage 设置/缓存、OPFS 下载与离线播放、封面取色、
      设置导入导出、歌词页和歌词展示层。页面隐藏、`pagehide`、`beforeunload` 均暂停播放，
      同时取消待完成的播放地址解析。
- [x] `Platform.isDesktop/isMobile` 接口与四平台 actual 一致化（2026-07-11）

### B. 需要外部环境
- [x] **iOS 构建与验证**（2026-08-21，macOS 26 / Xcode 26.5）：framework 链接、`iosSimulatorArm64Test`
      （374 通过）、真机签名构建与安装全部打通。封面取色已改用与 JVM/Web 共用的 RGB555 量化器，
      不再是 fallback。**尚未做**：真机运行时行为（AVPlayer 播放、Live Activity/灵动岛、
      NSURLSession 后台下载、麦克风识曲）仍需人工在设备上验证。
- [ ] **AGP 9.2.0 收敛**：仍卡上游（aapt2 解析 + 配置缓存）

### C. Windows 系统媒体协议
- [x] **Windows SMTC 直接绑定**（2026-07-11）：通过 JNA/COM WinRT
      `ISystemMediaTransportControlsInterop::GetForWindow` 绑定 Desktop 顶层 HWND，无需
      C++ helper DLL；Windows 11 + JDK 21 原生测试已验证 session 创建、元数据和 timeline
      发布；所有 WinRT 创建、更新和释放固定在同一 MTA 线程。`WinGuid` 必须保持非
      private，JNA 需要从自身模块反射 `Structure` 字段。

## 2.5 桌面 AMLL WebView 宿主（已于 2026-09-06 删除）

> 保留为历史记录。JavaFX WebKit 于 2026-07-05 淘汰，Windows x64 WebView2 于 2026-07-27
> 恢复为可选的 Legacy 渲染器，2026-09-06 与 Android System WebView、iOS `WKWebView`
> 一并删除。当前四端只有原生 Compose 一条路径。

删除范围：四个 `LyricScreen.*` 宿主、`WebviewJna` 绑定、`AmllWebBridge` 参数编码、
`AmllHostState` / `AmllSongDetails` 宿主状态、`AmllRendererSupport.*` 能力判定、
`amllAssets/amll/`（`player.html` / `bundle.js` / `style.css`）、`androidApp/amll-builder/`
Node 构建器、`webview-java` 依赖与其 JitPack 仓库、ProGuard keep 规则，以及
`compose.layers.type=COMPONENT` 启动 hack。

`DesktopOverlayWindow` 与 `ProvideDesktopOverlayOwner` 同时删除：它们存在的唯一原因是
WebView2 子 HWND 永远压在轻量 Compose 层之上，所有调用点都以 Legacy 是否激活为条件。
桌面端现在使用普通 Compose 页面转场、场景内 Snackbar 与 `ModalWideNavigationRail`。

页内 `#desktop-console` 播放控制台也一并删除，桌面端改用与 Android 相同的控制岛屿
`AutoHideMiniPlayerController`（岛屿是页内控制台的严格超集：额外提供折叠态、歌词详情
展开与封面取色）。

原宿主内容可用 `git show d3cfaf168d5f605d5bcea265055d84fedf691e6d:<path>` 取回。

## 2.6 原生 Compose AMLL 迁移（2026-07-27）

`commonMain/ui/amll/NativeAmllScreen.kt` 是四端唯一的全屏渲染器。
`MiniNowPlayingBar`、桌面播放条和系统/深链入口统一打开 `AmllPlayerScreen`，该路由直接
渲染 `NativeAmllScreen`，没有渲染器选项。

歌词引擎（解析、优化器、弹簧、时间线、布局、字级遮罩与强调、分词、减少动态效果、封面
背景）已于 2026-09-06 抽出到独立仓库 `RedSTARO/AMLL_Jetpack_Compose`，坐标
`com.leejlredstar.amll:amll-compose`，以 git 子模块 `AMLL_Jetpack_Compose/` 放在本仓库内，
由 `settings.gradle.kts` 的 `includeBuild("AMLL_Jetpack_Compose")` 接入。首次克隆需
`git submodule update --init`；引擎改动在子模块内提交推送后再更新本仓库的子模块指针。两侧的
Kotlin / Compose / AGP 版本和目标集必须保持一致。

| 项目 | 当前状态 |
|---|---|
| 响应式 AMLL 布局 | ✅ 全部由 common Compose 负责 |
| 歌词 | ✅ 共用逐行/逐字时序、翻译与罗马音、点击 seek、自动跟随与手动滚动恢复 |
| 视觉与动效 | ✅ 共用专辑色渐变、模糊封面、焦点行层级、无障碍与减少动态效果 |
| 控制岛屿 | ✅ `AutoHideMiniPlayerController` 四端同一份，统一按 Android 基准 3600 ms 自动收起 |
| 音乐百科 | ✅ `SongWikiDetailsButton` + `SongWikiDetailsSheet` 为共用响应式 Compose surface，并保留 media-ID 防串歌约束 |
| 动态封面 | ✅ 只保留 `NativeDynamicCoverLayer` 小型平台叶子；Android=Media3/TextureView、iOS=AVPlayerLayer/UIKitView、Desktop=FFmpeg 帧转 Compose ImageBitmap、Web=HTMLVideoElement/CanvasKit 互操作 |
| Desktop 层级 | ✅ 与其他端一致的普通 Compose 层级；顶层 overlay 窗口已随 WebView2 删除 |
| 路由收敛 | ✅ 一个渲染器、一个 `AmllPlayerScreen` 导航目的地；`FullLyricScreen` 已删除 |
| 本分支验证 | ✅ JVM/Android/WASM 编译、JVM/Wasm 浏览器测试及 Web 生产分发均通过；iOS Kotlin/Native 源码编译通过，framework/Xcode 应用链接和运行仍需 macOS |

## 3. 已知限制 / 合理偏差
- HttpClient 为启动时构建的 Koin 单例：改 server/cookie 下次启动生效（与原版 RetrofitInstance 一致）。
- 更新检查对比 GitHub `RedSTARO/RedefineNCM_KMP` releases/latest 与当前基础 tag；完整构建版本由 Git tag + commit hash 生成，形如 `v0.0.1.412ae548`。
- 换号时 KMP 会清缓存 UID 重新解析（原版 UID 永久缓存导致换号数据不刷新，属原版缺陷，已修正）。
- ~~`materialIconsExtended` 已弃用~~——已于 2026-07-04 迁移到自绘 Material Symbols（`ui/icon/AppIcons.kt`），依赖已移除。
- Web 网络请求受浏览器安全模型约束：API、音频和图片源必须允许 CORS；HTTPS 页面不能
  调用 HTTP 资源。Fetch 不能主动设置 `Cookie` 头，因此 Web 端使用 API 的 `cookie`
  查询参数，服务端必须兼容并避免长期记录该敏感参数。
- Web 首次播放受用户手势/自动播放策略约束；被拒绝时保持暂停。Media Session 与系统通知
  支持随浏览器而异，通知仅在已授权时发送，歌词页内展示不依赖这些 API。
- Web 下载依赖 HTTPS 或 `localhost` 安全上下文及 OPFS；下载文件属于站点私有存储，
  不出现在系统“下载”目录。清除站点数据会一并删除设置、缓存和离线歌曲。
- Web 包内含完整 Noto Sans SC 字体，用于动态中文歌名与歌词；启动页会等待字体下载和
  CanvasKit 解析完成后再显示 Compose 画布，避免首屏出现缺字方框。
