# RedefineNCM → KMP 迁移进度

> 最后更新：2026-06-11　｜　状态：**Android + Desktop 已构建验证通过**；iOS 代码就绪但需 macOS 构建。
> 权威细节与"目标规范 vs 现状差异清单"见 `AGENTS.md`（本仓库）与 `../RedefineNCM/AGENTS.md`（原始仓库）。
> 本文件只做"全步骤 / 已完成 / 剩余"的进度总览。

---

## 0. 构建验证状态（本机 Windows，可构建 Android + Desktop）

命令：`& "...\RedefineNCM_KMP\gradlew.bat" -p "...\RedefineNCM_KMP" <task>`

| 任务 | 结果 |
|---|---|
| `:shared:compileKotlinJvm` | ✅ 通过 |
| `:shared:compileAndroidMain` | ✅ 通过 |
| `:desktopApp:compileKotlin` | ✅ 通过 |
| `:androidApp:compileDebugKotlin` | ✅ 通过 |
| `:androidApp:assembleDebug`（完整 APK） | ✅ 通过 |
| `:shared:jvmTest`（含 **PlayQueueTest**，洗牌不变量回归测试） | ✅ 通过 |
| iOS（`iosSimulatorArm64Test` 等） | ⛔ 本机 Windows 无 Xcode，Gradle 自动禁用 |
| Web / wasmJs | ⏸ target 未声明（D2，最低优先级，暂缓） |

**当前工具链（已验证可用）**：Kotlin 2.4.0 ・ AGP 9.0.1 ・ Gradle 9.1.0 ・ Compose Multiplatform 1.11.1 ・
JB material3 1.11.0-alpha07 ・ Ktor 3.5.0 ・ Koin 4.2.1 ・ Coil 3.5.0 ・ kotlinx-serialization 1.11.0 ・
kotlinx-coroutines 1.11.0 ・ androidx.core 1.18.0 ・ datastore 1.2.0 ・ compileSdk/targetSdk 36 / minSdk 24。

> GUI 运行 `./gradlew :desktopApp:run`（或 `:desktopApp:hotRun --auto`）需在你的终端跑（有界面）。

---

## 1. 五大目标 — 全步骤总览

| # | 目标 | 状态 |
|---|---|---|
| 5 | 先更新/迁移 AGENTS.md | ✅ 完成（两仓库重写为权威规范 + 差异清单） |
| 4 | 升级所有依赖到最新 | 🟡 大体完成：原始仓库已升级；KMP 目录补全 + Kotlin 收敛 2.4.0（已验证）。**仅 AGP 9.2.0 暂缓**（上游问题） |
| 3 | 两仓库 UI 适配 Material 3 Expressive | ✅ 主题层完成（KMP 用真 `MaterialExpressiveTheme`；原始用公开 shape/type 标度） |
| 2 | 平台 now-playing：Android 通知→iOS 灵动岛 / 桌面悬浮窗 / Windows 媒体协议 | 🟡 桌面悬浮窗✅、Android 通知✅、iOS 灵动岛 Swift 源码✅(待 Xcode)、Windows SMTC 管线✅(待原生 helper) |
| 1 | 迁移到 KMP（iOS / desktop / android；web 次要） | 🟡 Android+Desktop 构建通过；核心屏幕(主页/搜索/歌单/登录/播放)+导航已补并验证；iOS 代码就绪待 Mac；真实音频后端 + User/Settings 待补 |

图例：✅ 完成　🟡 进行中/部分完成　⛔ 受外部环境阻塞　⏸ 暂缓

---

## 2. 已完成（且 Android+Desktop 构建验证）

### #5 AGENTS.md
- KMP 仓库 `AGENTS.md` 重写为"目标规范 + Current divergence from this spec（工作队列）"。
- 原始仓库 `AGENTS.md` 修正失实陈述（虚构的 SQLDelight/PlayQueue 引用、"依赖全最新"断言）。

### #4 依赖升级
- **原始 Android 仓库**：Coil 3.4.0→3.5.0、OkHttp 5.3.2→5.4.0；其余核实已是最新（AGP 9.2.0 / Kotlin 2.4.0 / Compose BOM 2026.05.x / media3 1.10.1 / Retrofit 3.0.0）。Room 保持 2.8.x（3.0 是破坏性 KMP alpha，不采用）。
- **KMP 仓库**：补全了代码实际 import 但之前缺失的全部依赖（Ktor / serialization+plugin / coroutines-core / Koin / Coil / datastore / material-icons-extended / androidx.core）；剔除未用的 multiplatform-settings / Voyager / SQLDelight 声明。
- **Kotlin 收敛**：KMP 2.3.21 → **2.4.0**（与原始仓库一致），CMP 1.11.0 → 1.11.1，均**构建验证通过**。

### #3 Material 3 Expressive
- KMP `ui/theme/Theme.kt` 用真正的 `MaterialExpressiveTheme` + `MotionScheme.expressive()` + Expressive shape/type 标度。
- 原始仓库主题用公开 shape/type 标度 + 动态取色（`MaterialExpressiveTheme` 在其 material3 1.4.0 里仍是 internal，升级到 1.5.0-alpha+ 才能切换，已注明）。
- 连接列表形状 `ui/component/Expressive.kt`（大外角/小内角）。

### #2 平台 now-playing 表面
- **桌面悬浮歌词窗口**：`desktopApp/main.kt` 开第二个无边框/半透明/置顶 Compose 窗口，由 `LyricNotificationController`(JVM actual) 驱动。✅ 构建验证。
- **Android 实况通知**：`AndroidNotificationController` 真实 `NotificationCompat` + 渠道 + Android 16 `setRequestPromotedOngoing` 实况通知；`RedefineNCMApp` 启动时 `init(context)`。✅ 构建验证。
- **iOS 灵动岛 / 锁屏 Live Activity**：Swift 源码完整（`iosApp/LyricWidget/` 的 ActivityKit widget + Dynamic Island UI + `LiveActivityManager`），Kotlin 侧 `startObserving/stopObserving` 桥接。源码就绪，**Xcode Widget Extension target 接线待 macOS**（见 `iosApp/LyricWidget/SETUP.md`）。
- **Windows SMTC（媒体协议）**：数据管线打通——`commonMain/smtc/MediaControlsIntegrator` ← `NowPlayingViewModel` 喂数据 → `jvmMain/smtc/WindowsMediaControls` 观察（`main.kt` 启动）。✅ 构建验证。**最后的 OS 推送需原生 WinRT helper DLL**（纯 JVM 无可运行验证路径，做法见 `WindowsMediaControls` KDoc）。

### #1 KMP 迁移（核心地基）
- 共享模块在 **common / Android / JVM(desktop)** 全部编译通过；Android APK 可打包。
- DI：Koin 图自洽，幂等 `initKoin()` 接入全部入口（desktop `main`、iOS `MainViewController`、Android `RedefineNCMApp`+`androidContext`）。
- `InMemoryPlatformPlayer`（基于已测 `PlayQueue` 的纯 Kotlin 参考播放器，无真实音频）已绑定 DI。
- `PlayQueue` + `PlayQueueTest`：洗牌/队列顺序不变量的纯 Kotlin 模型 + 回归测试（**测试通过**）。
- 网络层：`HttpClientFactory.create` 移植原始拦截器（baseUrl + realIP + timestamp + cookie），四个 platformModule 全部接入，base/cookie 取自 `PlatformSettings`。
- **导航 + 屏幕**：`App()` 手写 back stack 导航，入口 = `HomeScreen`；已有 Home / Search / PlaylistDetail / Login / NowPlaying 五屏（M3 Expressive），点歌→`setQueue`+`play`→NowPlaying，各屏可返回。
- API/DTO/Repository/各 ViewModel 全部交叉核对一致并编译通过。

---

## 3. 剩余步骤

### A. 本环境可做、且能构建验证（下一步重点）
- [x] **入口改为主页（Home）+ 导航**（2026-06-11，已构建验证）：`App.kt` 用手写 back stack 导航，入口落地 `HomeScreen`；可进 Login/Search/Playlist/NowPlaying，各屏有返回。
- [x] **新增屏幕**：`HomeScreen`（每日推荐 + 我的歌单 + 迷你播放 FAB）、`SearchScreen`（搜索 + 联想 + 结果）、`PlaylistDetailScreen`（歌单详情 + 播放全部）、`LoginScreen`（服务器 + cookie）；点歌即 `setQueue`+`play`→NowPlaying。均 M3 Expressive、已构建验证。
- [ ] **其余屏幕**：User（用户主页）、Settings（设置）、登录的 QR 扫码流程（`LoginViewModel` 已有 QR 字段，UI 未做）。
- [ ] **真实音频后端**（替换 `InMemoryPlatformPlayer`）：Android media3/ExoPlayer、JVM 桌面播放器（需音频库）。iOS AVPlayer 需 Mac 验证。
- [ ] **Coil 网络加载器** `coil-network-ktor3`：否则远程封面图不加载（当前仅 `coil-compose`）。
- [ ] **SQLDelight 缓存**（决策 D3）：插件 + `.sq` schema + driver + Repository 的 cache-then-network（当前 Repository 仅网络）。
- [ ] **`PlatformPlayer` 真实顺序逻辑委托给 `PlayQueue`**（现在各 actual 待实现时应复用 `PlayQueue`）。
- [ ] `shared/jvmMain` 补 `kotlinx-coroutines-swing`（否则 `:shared:jvmTest` 跑 `DesktopFloatingWindowController` 的 `Dispatchers.Main` 会缺失）。
- [ ] `Platform` 接口补 `isDesktop/isMobile`（带默认值），并修 wasm 占位代码的接口不一致。
- [ ] 清理模板残留 `Greeting.kt`/`GreetingUtil.kt`、`compose-multiplatform.xml`。

### B. 需要你的环境 / 上游修复（本机无法完成或验证）
- [ ] **iOS 构建与验证**：需 macOS + Xcode 16+。iOS 代码（`MainViewController`、各 actual、Live Activity Swift）已就绪；在 Mac 上开 `iosApp/iosApp.xcodeproj`，按 `iosApp/LyricWidget/SETUP.md` 加 Widget Extension target。
- [ ] **Windows SMTC 原生 helper**：C++/WinRT DLL（需 MSVC + Windows SDK），经 JNA 接入；做法见 `WindowsMediaControls` KDoc。
- [ ] **AGP 9.2.0 收敛**：要求 Gradle ≥9.4.1；升级 wrapper 后实测仍卡 `aapt2:9.2.0` 解析 + 配置缓存序列化（AGP 上游问题）。已回退到绿色的 9.0.1/9.1.0；待上游修复或 `--no-configuration-cache` 验证。
- [ ] **Web / wasmJs**（P3，最低优先级）：声明 `wasmJs` target + 加 `ktor-client-js`/`kotlinx-browser` + 修 `Platform.wasm.kt` 接口不一致。

---

## 4. 已知限制 / 与原版的合理偏差
- HttpClient 是 Koin 单例，启动时读 server/cookie：改设置后**下次启动生效**（与原 `RetrofitInstance` object 行为一致）。
- cookie 目前对所有请求附加（非空时），未按 `/login/*` 路径跳过（Ktor `defaultRequest` 读不到逐请求路径；需 send-pipeline 拦截器才能严格跳过）。
- `materialIconsExtended` 已弃用（固定 1.7.3，不再更新）——可用，后续宜迁移到 Material Symbols。
- 两仓库非 git 仓库，无法 commit。

---

## 5. 你可做的验证
1. `./gradlew :desktopApp:run`（你的终端，有界面）——看真实运行、DI/渲染是否正常。
2. 应用内填 cookie（存本机 `PlatformSettings`，不进仓库）→ 验证网络层联通。
3. macOS 上构建 iOS（见上）。
