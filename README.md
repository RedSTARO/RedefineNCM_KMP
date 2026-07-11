# RedefineNCM KMP

RedefineNCM 的 Kotlin Multiplatform 版本。Android、iOS、Desktop/JVM 与 Web/Wasm
共用 Compose Multiplatform UI、业务逻辑、播放队列和缓存模型；平台目录只承载音频、
通知/媒体控制、下载、存储等系统能力。

- `shared/src/commonMain`：共用 UI、API、Repository、播放器契约、下载编排与 SQLDelight 查询。
- `shared/src/androidMain`、`iosMain`、`jvmMain`、`wasmJsMain`：各平台 actual 实现。
- `androidApp`、`desktopApp`、`iosApp`：平台入口与原生外壳。Web 入口和静态资源位于
  `shared/src/wasmJsMain`。

## 构建与运行

```sh
# Android
./gradlew :androidApp:assembleDebug

# Desktop
./gradlew :desktopApp:hotRun --auto
./gradlew :desktopApp:run

# iOS（macOS + Xcode）
./gradlew :shared:iosSimulatorArm64Test

# Web 开发服务器
./gradlew :shared:wasmJsBrowserDevelopmentRun

# Web 浏览器测试与生产分发包
./gradlew :shared:wasmJsBrowserTest :shared:wasmJsBrowserDistribution
```

Web 生产文件输出到 `shared/build/dist/wasmJs/productionExecutable/`，可作为纯静态站点
部署；必须保留目录结构和 `.wasm` 文件的正确 MIME 类型。

## 测试

```sh
./gradlew :shared:testAndroidHostTest
./gradlew :shared:jvmTest
./gradlew :shared:iosSimulatorArm64Test
./gradlew :shared:wasmJsBrowserTest
```

## Web 行为与浏览器约束

Web 目标使用 `HTMLAudioElement` 播放、Media Session 提供系统媒体控制、OPFS 保存下载，
并用浏览器存储持久化设置和 SQLDelight 缓存。页面变为隐藏状态、进入 `pagehide` 或
触发 `beforeunload` 时会暂停播放，同时取消尚未完成的播放地址解析，避免退出后延迟起播。

浏览器能力有以下硬约束：

- API 服务、音频和图片资源必须允许 Web 站点来源的 CORS；HTTPS 页面不能访问 HTTP
  资源，否则会被浏览器的混合内容策略拦截。
- 浏览器禁止应用设置 `Cookie` 请求头，Web 端改用 API 支持的 `cookie` 查询参数；服务端
  必须兼容该参数，并应避免在访问日志中长期保存它。
- 首次播放通常需要用户手势。浏览器拒绝自动播放时，播放器保持暂停，等待用户再次点击。
- 内部下载需要 HTTPS 或 `localhost` 安全上下文及 OPFS 支持；文件保存在当前站点的
  origin-private 存储中，不会出现在系统“下载”目录。
- Media Session 和系统通知能力取决于浏览器；歌词始终显示在页面内，系统歌词通知仅在
  用户已授予通知权限时启用。
- Web 分发包包含完整 Noto Sans SC 字体以覆盖动态中文歌名和歌词；首次访问会先显示加载
  页，等字体完成下载和 CanvasKit 解析后再显示 Compose UI。

详细架构、行为约束和当前差异见 `AGENTS.md`，迁移进度见 `MIGRATION_PROGRESS.md`。
