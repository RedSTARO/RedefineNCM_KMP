# RedefineNCM → KMP 迁移进度

> 最后更新：2026-07-11　｜　状态：**四目标均已启用，共用功能行为对齐**；Android + Desktop + Web 编译验证通过；iOS 代码就绪但需 macOS 构建。
> 权威细节与"目标规范 vs 现状差异清单"见 `AGENTS.md`（本仓库）与 `../RedefineNCM/AGENTS.md`（原始仓库，已冻结）。
> 本文件只做"全步骤 / 已完成 / 剩余"的进度总览。

---

## 0. 构建验证状态（本机 Windows，可构建 Android + Desktop + Web）

| 任务 | 结果 |
|---|---|
| `:shared:compileKotlinJvm` | ✅ 通过（2026-07-04） |
| `:androidApp:assembleDebug`（完整 APK） | ✅ 通过（2026-07-04） |
| `:shared:jvmTest`（含 **PlayQueueTest**） | ✅ 通过（2026-07-04） |
| iOS（`iosSimulatorArm64Test` 等） | ⛔ 本机 Windows 无 Xcode，Gradle 自动禁用 |
| `:shared:compileKotlinWasmJs` | ✅ 通过（2026-07-11） |
| `:shared:wasmJsBrowserTest` + `:shared:wasmJsBrowserDistribution` | ✅ 本机通过（2026-07-11）；生产目录为 `shared/build/dist/wasmJs/productionExecutable/` |

**工具链**：Kotlin 2.4.0 ・ AGP 9.0.1 ・ Gradle 9.1.0 ・ CMP 1.11.1 ・ JB material3 1.11.0-alpha07 ・
Ktor 3.5.0 ・ Koin 4.2.1 ・ Coil 3.5.0（含 coil-network-ktor3）・ SQLDelight 2.3.2 ・
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
| 全屏播放器（AMLL/Compose fallback、进度、控制、队列 sheet、评论 sheet） | ✅（MiniNowPlaying 与系统入口直接打开；旧 KMP NowPlaying 已移除） |
| 随机播放不变量（播放顺序队列 + 高亮同源重建） | ✅（ExoPlayer 按 timeline 播放顺序重建 + skipToIndex 映射窗口索引；VM 实时订阅） |
| 播放状态持久化（队列/索引/进度/shuffle，onPause 存、启动恢复不自动播） | ✅（PlayerStatus.sq + PlatformPlayer.restoreQueue） |
| 歌单批量下载（5 首/批，跳过已存在） | ✅（common 编排；Android MediaStore/JVM 文件系统/iOS NSURLSession/Web OPFS） |
| 离线播放（本地已下载文件优先） | ✅（此前已完成） |
| Room 缓存（cache-then-network） | ✅ SQLDelight 9 表（含 CommentMusic、PlayerStatus） |
| QR 登录 + Cookie 登录 | ✅（修复了二维码 bitmap 从未赋值导致不显示的 bug） |
| UID 解析（缓存 → /user/account 兜底） | ✅（修复原 stub 不调 API 的问题；另支持换号后 refreshAccount） |
| 设置页（server 检查按钮 + 全部开关 + 备份导入导出） | ✅（含 adaptOriginalAndroidLyric 开关，与原版一样暂无消费方） |
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
- [ ] **iOS 构建与验证**：需 macOS + Xcode 16+（AVPlayer、Live Activity、NSURLSession 后台下载源码已就绪；专辑色提取仍为 fallback）
- [ ] **AGP 9.2.0 收敛**：仍卡上游（aapt2 解析 + 配置缓存）

### C. Windows 系统媒体协议
- [x] **Windows SMTC 直接绑定**（2026-07-11）：通过 JNA/COM WinRT
      `ISystemMediaTransportControlsInterop::GetForWindow` 绑定 Desktop 顶层 HWND，无需
      C++ helper DLL；Windows 11 + JDK 21 原生测试已验证 session 创建、元数据和 timeline
      发布；所有 WinRT 创建、更新和释放固定在同一 MTA 线程。`WinGuid` 必须保持非
      private，JNA 需要从自身模块反射 `Structure` 字段。

## 2.5 桌面 AMLL 实机调试（2026-07-04，computer-use 实机验证通过）

以真实 cookie 登录桌面端实机排查 AMLL 歌词页，修复了一条完整的故障链，最终
歌词渲染/滚动/返回全部验证通过：

| 症状 | 根因 | 修复 |
|---|---|---|
| AMLL 页纯白屏 | JavaFX D3D 管线下 WebKit `RTImage.getTexture` NPE——Prism 纹理池默认 512M 对大 WebView 表面不够 | `main()` 设 `prism.maxvram=2G`（不要用 `prism.order=sw`：软件渲染会打满 CPU 并饿死网络协程） |
| 退出歌词页整个 JVM 崩溃 | `onDispose { engine.load("about:blank") }` 触发 WebKit native 崩溃（`twkOpen → fwkDisposeGraphics`） | dispose 只清空歌词行，不重载页面 |
| 歌词永远加载不出（连环 ConnectTimeout） | 服务器 DNS 有黑洞 A 记录（43.174.246.32 不通），CIO 引擎无多地址回退 + JVM DNS 缓存 30s 覆盖全部重试窗口 | JVM 端 Ktor 引擎 CIO → **OkHttp**（RouteSelector 自动换 IP，与 Android 一致）；另配 `HttpTimeout`（connect 20s）+ 歌词获取失败重试 4 次 |
| 网络请求跑在 Swing EDT | VM scope 是 `Dispatchers.Main`（桌面=EDT） | 两个 VM 的网络获取统一 `launch(Dispatchers.Default)`（播放器控制保留 Main） |
| 迷你播放条撑满整窗 | FAB slot 无高度约束 + 内层 `fillMaxHeight()`（825c22c 修过的回归） | Surface 固定 `size(300×112dp)`（原版尺寸） |
| 桌面恢复队列后自动出声 | `restoreQueue` 默认实现 setQueue→pause 与异步解析竞态 | `JvmMediaPlayer` 覆写 `restoreQueue`：装载不播放，play 时从记忆位置续播 |

### 第三轮（2026-07-05）：AMLL 迁移到系统 WebView2（实机验证通过）

JavaFX WebKit 天花板确认（无 GPU 合成：字体/布局/动画残缺，关掉特效才有帧率）→
迁移到 **系统 WebView（Windows = WebView2 / Edge Chromium）**，AMLL 完整效果
（无衬线粗体、弹簧滚动、逐行模糊渐隐、blur 封面背景）实机验证通过：

- 依赖 `webview_java`（JitPack commit 坐标 + Casterlabs 仓库补传递依赖），只用其
  打包的 native dll 与 JNA；绑定层为自写精简 Kotlin `WebviewJna`。
- **打包 dll 的嵌入分支已损坏**：window 参数无论直传 HWND 还是包 HWND* 在纯 AWT
  下都以 JNA "Invalid memory access" 崩（C++ 异常穿 C ABI）；只有 window=null 的
  自建窗口路径稳定 → 采用 **自建窗口 + Win32 SetParent 收编** 为 AWT Canvas 子窗口
  （dll 自身 WndProc/DPI/WM_SIZE→resize 逻辑全保留）。
- JAWT 句柄需 GetClassName 校验，失效时回退 EnumChildWindows 找 SunAwt* 子窗口。
- AWT 报逻辑尺寸（DIP），MoveWindow 需物理像素 —— 乘 graphicsConfiguration
  defaultTransform 缩放（150% DPI 下否则只铺 2/3）。
- bind/dispatch 的 JNA 回调必须持强引用（native 侧存指针，GC 掉即崩）。
- player.html 恢复完整特效（blur(48px) 背景等），signalReady 增加 amllReady 通道；
  移除 JavaFX 六模块依赖与 prism 系 hack。

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
