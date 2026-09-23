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

Web 生产文件输出到 `shared/build/dist/wasmJs/productionExecutable/`，可以按纯静态站点部署。
部署时必须保留目录结构，`.wasm` 文件也要使用正确的 MIME 类型。

## 测试

```sh
./gradlew :shared:testAndroidHostTest
./gradlew :shared:jvmTest
./gradlew :shared:iosSimulatorArm64Test
./gradlew :shared:wasmJsBrowserTest
```

## 歌词来源与 AMLL

设置页可选择四种歌词策略：

- AMLL TTML 优先，现有后端回退（默认）；
- 现有后端优先，AMLL TTML 回退；
- 仅 AMLL TTML；
- 仅现有后端。

AMLL TTML 按当前网易云歌曲 ID 精确查询
[`amll-dev/amll-ttml-db`](https://github.com/amll-dev/amll-ttml-db)，不自动套用按标题模糊匹配的结果。
查询用一个独立的无凭证客户端，只发送歌曲 ID，不会把网易云 Cookie、`realIP` 或服务器地址发给第三方。
Android、iOS、Desktop/JVM 与 Web/WASM 共用同一套 `NowPlayingViewModel` 歌词状态与来源策略。

迷你播放条、桌面侧栏和系统的「正在播放」入口先打开 `ui/screen/NowPlayingScreen.kt`，
即 Material 3 Expressive 风格的 Now Playing 页（封面、标题、波形进度、变形的播放控件、浮动工具栏）；
工具栏上的英文引号按钮进入全屏歌词页。歌词页在四端是同一份原生 Compose 实现
`commonMain/ui/amll/NativeAmllScreen.kt`，项目中不使用任何 WebView。

歌词引擎是独立仓库 [AMLL_Jetpack_Compose](https://github.com/RedSTARO/AMLL_Jetpack_Compose)，
以 git 子模块的形式放在本仓库的 `AMLL_Jetpack_Compose/` 目录，由 `settings.gradle.kts` 的
`includeBuild` 以 composite build 方式接入（坐标 `com.leejlredstar.amll:amll-compose`）。
引擎按源码翻译自固定版本的 `@applemusic-like-lyrics/core 0.5.2`、
`@applemusic-like-lyrics/lyric 1.0.2` 与 `@applemusic-like-lyrics/ttml 1.0.1`。

首次克隆后运行 `git submodule update --init`。修改歌词引擎时，先在子模块内提交并推送，
再在本仓库提交新的子模块指针。改了歌词解析、布局、弹簧或逐字渲染之后，运行：

```sh
cd AMLL_Jetpack_Compose && ./gradlew :amll-compose:jvmTest
./gradlew :shared:jvmTest
./gradlew :shared:testAndroidHostTest
./gradlew :shared:wasmJsBrowserTest
```

CI 验证 TTML → 公共歌词模型 → Compose 渲染字段链路，并校验应用内许可证资源与仓库
声明一致。宿主侧映射见
[`docs/AMLL_NATIVE_TRANSLATION.md`](docs/AMLL_NATIVE_TRANSLATION.md)；歌词引擎的逐符号
映射与平台 API 边界以子模块 `AMLL_Jetpack_Compose/docs/AMLL_NATIVE_TRANSLATION.md` 为准。

AMLL 包声明为 `AGPL-3.0-only`；完整文本和上游版本、源码位置见
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。仓库目前没有项目级 `LICENSE`，
所以发布二进制之前，项目权利人还要确定兼容的项目许可和 Corresponding Source 范围；
集成 AMLL 时没有替权利人做这个许可决定。`LICENSES/` 和各应用资源里有当前直接集成组件的
许可证文本，但它们不能代替发布前对完整依赖图和二进制组合的许可审计。权利人记录这一决定之前，
标签 CI 会拒绝创建公开的二进制 Release；权利人完成记录和审计后，才能把仓库变量
`RELEASE_LICENSE_APPROVED` 明确设为 `true`。

## 听歌识曲

首页「音乐工具」里有听歌识曲入口。Android、iOS、Desktop/JVM 和 Web 的录音链路都用
Kotlin 源码实现；共用代码把每次三秒的录音转换为 8 kHz 单声道 PCM，生成音频指纹后调用
当前服务器的 `POST /audio/match?duration=3&audioFP=...`。Web 端通过 Kotlin/Wasm typed
`external` 声明调用 `MediaRecorder` 和 Web Audio，识曲部分没有 JS/TS 实现。指纹算法的来源和
MIT 许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 播放上报

登录后真正开始播放时，共用协调器会为每次选曲建立独立会话：

- 稳定进入 `PLAYING` 后立即通过 `POST /weblog` 发送一次 `startplay`，让该曲进入账号的
  「最近播放」；暂停/恢复、拖动和 shuffle 不会重复发送；
- `/relay/play/state/submit` 在开始、暂停/模式变化、结束以及播放中每 30 秒提交进度；
- `/scrobble/v1` 在有效播放进度达到歌曲时长一半后至多尝试一次，拖动、缓冲、卡住和
  系统休眠时间不计入；
- `startplay` 只负责最近播放，半程听歌记账只通过 `/scrobble/v1`；任一接口的 HTML 404
  都会直接报告接口不支持，不会互相回退；
- 歌单播放会携带 `sourceid=歌单 ID`，`source` 使用后端默认值 `list`；
- 没有歌单来源的首页推荐、搜索或识曲结果使用歌曲 ID 作为 `startplay` 来源兜底；
- Web 和原生端都把请求绑定到会话建立时的 Cookie 快照，换号后的旧请求不会借用新账号凭证；
- 每次上报都记录结果：页面区分发送中、服务器接收、账号侧确认、回读未反映、拒绝、接口不支持
  和传输失败，并保留 HTTP/body code 和有界的响应详情；
- 服务器接收 scrobble 后，协调器会回读 `/user/record`、`/record/recent/song` 和 `/user/level`。
  只有目标歌曲的周记录或最近播放发生变化，才算账号侧确认；等级和播放数会强制刷新到「我的」页，
  但单凭它们不能证明这首歌记账成功。`回读未反映` 只说明在有界窗口内没有取得可归因的证据，
  不能证明服务端最终没有异步入账。

2026-07-13 局域网后端升级到 `4.36.2` 后，路由探测确认 `/scrobble/v1` 和
`/relay/play/state/submit` 都已注册。当前上游源码也实现了 `/weblog`。同样形状的 `startplay`
请求曾在旧后端上作为两段式链路的第一步通过受控验证，但没有单独隔离验证过；升级后的单次
`startplay` + 半程 NCBL 链路也还没有在真实账号侧复验。

当前服务有 relay submit，但没有拉取当前 `progress/sessionId/playMode` 的接口，所以应用只能
提交远端状态，做不到官方客户端那样精确的双向播放进度漫游或远端队列恢复。历史/最近播放接口
不含这些字段，客户端也不会拿它们的数据充当完整的跨端状态。

## 心动模式

「我的」页里，「我喜欢的音乐」卡片右侧的爱心可以直接启动心动模式。应用从当前账号的红心
歌曲中随机选一首作种子，调用 `/playmode/intelligence/list`，按服务端给出的顺序替换播放队列
并立即播放。爱心的加载状态和失败重试不影响整张卡片「打开歌单详情」的行为；智能列表为空
或请求失败时，现有播放队列保持不变。

## Web 行为与浏览器约束

Web 目标用 `HTMLAudioElement` 播放，由 Media Session 提供系统媒体控制，用 OPFS 保存下载，
并用浏览器存储持久化设置和 SQLDelight 缓存。页面变为隐藏状态、进入 `pagehide` 或
触发 `beforeunload` 时会暂停播放，同时取消尚未完成的播放地址解析，避免退出后延迟起播。

浏览器带来以下硬性约束：

- API 服务、音频和图片资源必须允许 Web 站点来源的 CORS；
  HTTPS 页面访问 HTTP 资源会被浏览器的混合内容策略拦截。
- 听歌识曲的麦克风仅在 HTTPS 或 `localhost` 安全上下文可用，并需要站点麦克风权限；
  HTTPS 部署时 `/audio/match` 后端也必须使用 HTTPS，否则会被混合内容策略拦截。
- 浏览器禁止应用设置 `Cookie` 请求头，Web 端改用 API 支持的 `cookie` 查询参数；服务端
  必须兼容该参数，并应避免在访问日志中长期保存它。
- 首次播放通常需要用户手势。浏览器拒绝自动播放时，播放器保持暂停，等待用户再次点击。
- 内部下载需要 HTTPS 或 `localhost` 安全上下文及 OPFS 支持；文件保存在当前站点的
  origin-private 存储中，不会出现在系统「下载」目录。
- Media Session 和系统通知能力取决于浏览器；歌词始终显示在页面内，系统歌词通知仅在
  用户已授予通知权限时启用。
- Web 分发包包含完整 Noto Sans SC 字体，用来覆盖动态的中文歌名和歌词；首次访问会先显示
  加载页，等字体下载完成、CanvasKit 解析完毕后再显示 Compose UI。

详细架构、行为约束和当前差异见 `AGENTS.md`，迁移进度见 `MIGRATION_PROGRESS.md`。
