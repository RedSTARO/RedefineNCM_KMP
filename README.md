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

## 听歌识曲

首页“音乐工具”提供听歌识曲入口。Android、iOS、Desktop/JVM 和 Web 的录音链路均以
Kotlin 源码实现；共用代码将本次三秒录音转换为 8 kHz 单声道 PCM，生成音频指纹后调用
当前服务器的 `POST /audio/match?duration=3&audioFP=...`。Web 端通过 Kotlin/Wasm typed
`external` 声明调用 `MediaRecorder` 和 Web Audio，没有新增 JS/TS 识曲实现。指纹算法来源及
MIT 许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 播放上报

登录后真正开始播放时，共用协调器会为每次选曲建立独立会话：

- 稳定进入 `PLAYING` 后立即通过 `POST /weblog` 发送一次 `startplay`，让该曲进入账号的
  “最近播放”；暂停/恢复、拖动和 shuffle 不会重复发送；
- `/relay/play/state/submit` 在开始、暂停/模式变化、结束以及播放中每 30 秒提交进度；
- `/scrobble/v1` 在有效播放进度达到歌曲时长一半后至多尝试一次，拖动、缓冲、卡住和
  系统休眠时间不计入；
- `startplay` 只负责最近播放，半程听歌记账只通过 `/scrobble/v1`；任一接口的 HTML 404
  都会直接报告接口不支持，不会互相回退；
- 歌单播放会携带 `sourceid=歌单 ID`，`source` 使用后端默认值 `list`；
- 没有歌单来源的首页推荐、搜索或识曲结果使用歌曲 ID 作为 `startplay` 来源兜底；
- Web 和原生端都把请求绑定到会话建立时的 Cookie 快照，换号后的旧请求不会借用新账号凭证；
- 上报结果不再丢弃：页面会区分发送中、服务器接收、账号侧确认、回读未反映、拒绝、接口不支持
  和传输失败，并保留 HTTP/body code 及有界响应详情；
- scrobble 被接收后会回读 `/user/record`、`/record/recent/song` 和 `/user/level`。只有目标歌曲
  的周记录或最近播放发生变化才算账号侧确认；等级和播放数会强制刷新到“我的”页，但不会被单独
  当作本次歌曲记账成功的证据。`回读未反映` 只表示有界窗口内没有取得可归因证据，不等于证明
  服务端最终没有异步入账。

2026-07-13 局域网后端升级到 `4.36.2` 后，路由探测确认 `/scrobble/v1` 和
`/relay/play/state/submit` 均已注册。当前上游源码也实现了 `/weblog`；相同的
`startplay` 请求形状曾作为旧后端两段式链路的第一步受控验证成功，但未曾单独隔离
验证，升级后的单次 `startplay` + 半程 NCBL 链路也尚未完成真实账号侧复验。

当前服务已有 relay submit，但没有拉取当前 `progress/sessionId/playMode` 的接口；所以这里只能
提交远端状态，无法实现官方客户端级别的
精确双向播放进度漫游或远端队列恢复。历史/最近播放接口不包含这些字段，客户端不会把它们伪装成
完整跨端状态。

## 心动模式

“我的”页中，“我喜欢的音乐”卡片右侧爱心可直接启动心动模式。应用从当前账号的红心
歌曲中随机选择种子，调用 `/playmode/intelligence/list`，按服务端顺序替换播放队列并立即
播放。爱心加载状态与失败重试不会改变整张卡片原有的“打开歌单详情”行为；智能列表为空
或请求失败时，现有播放队列保持不变。

## Web 行为与浏览器约束

Web 目标使用 `HTMLAudioElement` 播放、Media Session 提供系统媒体控制、OPFS 保存下载，
并用浏览器存储持久化设置和 SQLDelight 缓存。页面变为隐藏状态、进入 `pagehide` 或
触发 `beforeunload` 时会暂停播放，同时取消尚未完成的播放地址解析，避免退出后延迟起播。

浏览器能力有以下硬约束：

- API 服务、音频和图片资源必须允许 Web 站点来源的 CORS；HTTPS 页面不能访问 HTTP
  资源，否则会被浏览器的混合内容策略拦截。
- 听歌识曲的麦克风仅在 HTTPS 或 `localhost` 安全上下文可用，并需要站点麦克风权限；
  HTTPS 部署时 `/audio/match` 后端也必须使用 HTTPS，否则会被混合内容策略拦截。
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
