# 多平台聚合与账号功能审查

- 基线：`main` @ `1ef849a`（2026-09-22）加工作区未提交改动：新增 `AccountsScreen.kt`、`LocalAccount.kt`；修改 `App.kt`、`Modules.kt`、`ProviderLoginDescriptor.kt`、`SettingsScreen.kt`、`Settings.kt`、`PushedDestinationCodecTest.kt`。行号按该工作区。
- 方法：只读源码。未运行应用，未连接 QQ 网关，未执行测试。标【推断】的条目依赖未核实的外部行为（网关 CORS、浏览器对 `Cookie` 请求头的处理、网关对过期 key 的应答、两个平台搜索结果的实际重合度）。
- 范围：`data/provider`、`data/auth`、`ui/login`；账号、登录、设置、搜索页；四个平台的取流分发；播放页里依赖歌曲 id 的功能；设置备份。
- 「多账号」按 D6 的定义：每个平台一个账号，加一个本地账号。同一平台多个账号是 D6 明确排除的，本文不把它作为建议，只在第 6 节列出。
- 影响类型（描述性分类，不代表优先级）：
  - **缺陷**：行为与代码自身的意图或文档不符。
  - **摩擦**：能用，但结果与操作预期不一致，或需要多余步骤。
  - **一致性**：同一概念在不同位置的判定、文案、实现不同。
  - **结构**：不影响当前行为，影响增加平台或功能时的改动面。
- 「已记录」：AGENTS.md D6「Still open」已经列出的项，单列在第 3 节，只确认是否仍成立。

---

## 1. 现状结构

### 1.1 组件与生产调用方

| 层 | 组件 | 生产代码里的调用方 |
|---|---|---|
| 标识 | `ProviderItemId`、`MusicProviderId`；`MediaInfo.id` 对网易云是裸 id，对其他平台带前缀 | 全局 |
| 平台抽象 | `MusicProviderRegistry.searchAll` | `MainViewModel.search`、`loadMoreSearchResults` |
| | `MusicProviderRegistry.streamUrl` | `resolveStreamUrl`（四个平台的播放器），只处理非网易云 id |
| | `MusicProviderRegistry.lyric`、`.playlistDetail` | 无（`shared/src` 全部源集、`androidApp`、`desktopApp` 中均无调用） |
| | `NeteaseProvider` | 只经 `searchAll` 用到 `search`；网易云取流走各平台自己的路径 |
| 凭证 | `CredentialStore` → `NeteaseCredentialSlot`、`QQCredentialSlot` | 登录页（`LoginViewModel.persist`）、账号页、`QQProvider` 续期 |
| 登录 | `LoginMethodRegistry`（6 个方法）、`LoginPresenterRegistry`（3 种形态）、`QrLoginFlow`、`PhoneCodeLoginFlow` | 登录页 |
| 文案 | `ProviderLoginDescriptorRegistry` | 登录页、账号页 |
| 本地账号 | `LocalAccount`（只有名称） | 账号页（未提交） |
| 设置键 | `cookie` / `server`；`qqEnabled` / `qqServer` / `qqCookie`；`libraryAggregationMode`；`localAccountName`（未提交） | 设置页、账号页、登录页、备份 |

### 1.2 新增一个平台需要改的位置

1. `MusicProviderId` 枚举项。
2. 一个 `MusicProvider` 实现，加入 `MusicProviderRegistry` 的列表。
3. 一个 `ProviderCredentialSlot`，加入 `CredentialStore` 的列表。
4. 一个 `ProviderLoginDescriptor`。
5. 若干 `LoginMethod`，加入 `LoginMethodRegistry`。
6. `SettingKeys` 里的开关、地址、凭证键。
7. `SettingsBackupData` 的字段与导入导出。
8. `SettingsScreen` 里的「启用」开关与后端地址（QQ 目前是写死的一段）。

2–5 在 `Modules.kt` 的四个列表里分别登记，漏登由运行时的 `require(...)` / `error(...)` 发现（`CredentialStore.require`、`ProviderLoginDescriptorRegistry.require`）。6–8 没有集中入口。

### 1.3 网易云专属功能的判定方式

「当前曲目是不是网易云的」没有类型上的表示，由各处把 `MediaInfo.id` 解析成 `Long` 来隐式判定：

- `NowPlayingViewModel.kt` 9 处：`248`（本地下载判定，决定本地歌词优先与本地封面），`540`、`562`、`572`（歌词），`710`（歌词预取），`805`、`860`（评论），`918`（歌曲详情），`963`（喜欢）。
- `NowPlayingScreen.kt:535`（歌手 / 专辑菜单）。
- `SharedScreenParts.kt:152`（`neteaseSong != null` 决定下载、复制链接、歌手、专辑四个菜单项）。
- `SearchScreen.kt:592`、`609-627`。
- `PlaybackReportingCoordinator.kt:633`（用 `neteaseIdOrNull`，是显式的）。

解析失败时各处的处理方式不同，见 2.1。

---

## 2. 新发现（D6 未记录）

### 2.1 QQ 曲目在播放页的表现

| # | 现象 | 位置 | 影响 |
|---|---|---|---|
| P1 | 歌词页：`fetchLyrics` 解析不出数字 id，写入 `LyricUiState.Error("Current media id is invalid")`；歌词页显示「歌词加载失败」、英文内部信息和重试图标，重试走同一路径，结果相同。`QQProvider.lyric` 已实现，未接入。 | `NowPlayingViewModel.kt:593-596`，`LyricStateOverlay.kt:78-82` | 缺陷 |
| P2 | 歌曲详情：显示错误「Current song id is invalid」（英文）。 | `NowPlayingViewModel.kt:918-924` | 缺陷 |
| P3 | 喜欢：`onFavClick` 在 id 解析失败时直接返回；心形按钮照常显示、可点，点后无反馈。 | `NowPlayingViewModel.kt:960-963`，`NowPlayingScreen.kt:180` | 摩擦 |
| P4 | 评论：`getComments` 直接返回，不设加载或错误状态；评论面板显示「暂无评论」。 | `NowPlayingViewModel.kt:802-805`，`PlaybackBottomSheets.kt:532` | 一致性 |
| P5 | 取流失败不给原因：VIP 限制（网关 `result 104003`）、QQ 已关闭（`isAvailable()` 为 false）、网关不可达，在 `QQMusicApi.songUrl` / `QQProvider.streamUrl` 处都变成 `null`；Android 抛 `IOException("Failed to resolve stream URL…")`，桌面置 `PlayerState.ERROR`，界面不说明是哪一种。 | `QQMusicApi.kt:66-74`，`QQProvider.kt:104-112`，`RedirectingDataSource.kt:42-43`，`JvmMediaPlayer.kt:403-409` | 摩擦 |
| P6 | 歌手 / 专辑菜单对 QQ 曲目禁用点击（`enabled = songId != null`）。同一类「不支持」，这里是禁用，P3 是可点无反应，P1 / P2 是报错。 | `NowPlayingScreen.kt:535-548` | 一致性 |

P1–P6 同出一处：没有一个地方能回答「当前曲目支持哪些功能」，每个功能各自解析 id、各自决定失败时的表现（1.3）。

### 2.2 搜索聚合

| # | 现象 | 位置 | 影响 |
|---|---|---|---|
| S1 | 翻页用一个共享的 `searchOffset`，每次对所有可用平台请求同一 offset。第一页失败的平台，在「加载更多」时直接被请求第二页，它的第一页不会再出现；第一页已不足 30 条（已到底）的平台仍被继续请求。加载更多时只有全部平台都失败才提示，单个平台失败无提示。`ProviderSearchPagingTest` 只覆盖两个平台都返回满页的情况。 | `MainViewModel.kt:750-770`，`ProviderSearchPagingTest.kt:26-37` | 缺陷 |
| S2 | 合并视图按平台交替排列，不识别跨平台的同一首歌；同一首热门歌曲可能在第 1、2 位各出现一次。【推断】 | `MusicProviderRegistry.kt:122-133` | 摩擦 |
| S3 | 在合并视图点一首歌，整个交替列表进入播放队列，队列里混有网易云和 QQ 曲目，其中的 QQ 曲目逐首遇到 2.1 的情况。按平台分组视图只放入该组。 | `SearchScreen.kt:239-248`，`SearchScreen.kt:286-296` | 摩擦 |
| S4 | 搜索建议和热搜只来自网易云（`repo.searchSuggest`、`repo.searchHot`）。 | `MainViewModel.kt:835-854` | 一致性 |
| S5 | 网易云的 `isAvailable()` 恒为 `true`，没有「启用」开关。只用 QQ 的用户在网易云后端不可达时，每次搜索都显示「网易云音乐 搜索失败，仅显示其他平台结果」。 | `NeteaseProvider.kt:34`，`MainViewModel.kt:721-729` | 摩擦 |

### 2.3 凭证与登录

| # | 现象 | 位置 | 影响 |
|---|---|---|---|
| C1 | QQ 凭证续期按进程只尝试一次：`renewalAttempted` 在第一次调用时置位，与当时有没有凭证、是哪个凭证无关。之后在同一进程内登录、换账号、粘贴旧凭证，都不会再检查过期，直到重启。 | `QQProvider.kt:32-33`，`QQProvider.kt:123-139` | 缺陷 |
| C2 | 续期写回不比较当前值：若在 `refreshCredential()` 返回与 `slot.write` 之间用户退出或换了账号，旧账号会被写回。窗口只在这两步之间（续期整体上限 8 s），概率低。 | `QQProvider.kt:128-137` | 缺陷（低概率） |
| C3 | 没有被动续期：会话中途 key 过期后，直到重启都不会再续期。过期 key 的请求由网关按匿名处理还是答 401【推断，未核实】；后者在 `fetchEnvelope` 里变成 `null`。 | `QQMusicApi.kt:132`，`QQProvider.kt:123-139` | 摩擦 |
| C4 | 续期没有测试（`commonTest` / `jvmTest` 中搜不到 `renew`、`check_expired`、`refreshCredential`）。 | — | 结构 |
| C5 | `QQQrLoginMethod.unansweredPolls` 是单例里（`LoginMethodRegistry` 为 Koin `single`）按二维码 token 计数的可变 map；离开页面时计数未满 3 次的 token 不会被移除。`LoginMethod` 的注释约定方法只放逻辑、状态归 flow，这里不符。 | `QQLoginMethods.kt:39-52`，`Modules.kt:157-168` | 结构 |
| C6 | Web：QQ 的登录方法在所有平台注册，`WasmPlatformModule` 不覆盖；`QQMusicApi` 用 `Cookie` 请求头传账号，fetch 不能设置这个头。【推断】Web 端的登录流程可以走完、账号页显示已登录，之后登录状态下的 QQ 请求有两种可能：浏览器丢弃该头，请求按未登录处理；或 fetch / Ktor 在设置该头时报错，请求全部失败，界面显示「QQ音乐后端无响应」。两种都未核实。AGENTS.md 写明 Web 对 QQ 是匿名的，界面没有体现。网关是否允许 Web 源跨域也未核实。 | `Modules.kt:157-168`，`QQMusicApi.kt:127`，`WasmPlatformModule.kt` | 一致性 |
| C7 | `MusicProviderRegistry` 的 `available` / `streamUrl` / `lyric` / `playlistDetail` / `searchAll` 用 `runCatching` 包裹，会吞掉 `CancellationException`；`QQMusicApi.fetchEnvelope` 为此专门重新抛出，注释写明了吞掉它造成过的问题。桌面播放器有 generation 校验，目前未见实际影响。 | `MusicProviderRegistry.kt:47`、`63`、`68`、`73`、`93`；`QQMusicApi.kt:135-138` | 一致性 |

### 2.4 设置与备份

| # | 现象 | 位置 | 影响 |
|---|---|---|---|
| T1 | 后端地址有两个编辑入口：设置页（网易云 `SettingsScreen.kt:783`、QQ `862-874`，经 `normalizeServerInput` 补尾部 `/`）与登录页（`LoginViewModel.saveServer`，只 trim）。两种写法目前都不改变请求结果（`NCMApi` 用绝对路径，`QQMusicApi` 会 `trimEnd('/')`）；问题在于同一个设置由两段代码维护。 | `SettingsScreen.kt:1123-1126`，`LoginViewModel.kt:76-96` | 一致性 |
| T2 | 备份里 `qqEnabled` 是非空 `Boolean`（默认 `false`），导入 D6 之前的备份会把 QQ 关掉；同一文件把 `libraryAggregationMode` 设为可空，注释写明就是为了导入旧备份时保留当前值。两个字段的约定不同。 | `SettingsBackup.kt:19-22`，`SettingsBackup.kt:112-114` | 一致性 |

### 2.5 未提交改动（账号页）

| # | 现象 | 位置 | 影响 |
|---|---|---|---|
| U1 | 配置 QQ 分散在三处：设置页（启用、后端地址、分组方式）、账号页（登录、手动凭证）、登录页（后端地址）。 | `SettingsScreen.kt:850-895`，`AccountsScreen.kt:151-218`，`LoginScreen.kt:172-181` | 摩擦 |
| U2 | 设置页的账号摘要行写死「网易云 / QQ 音乐 / 本地账号」三段文案，并在 `SettingsScreen` 里直接解析 `QQCredential`。descriptor 注册表引入的目的就是让设置页不含平台分支。 | `SettingsScreen.kt:454-469` | 结构 |
| U3 | 「已登录」有两种判定口径：设置页摘要用 `QQCredential.parse(...) != null`，账号页和 `LoginViewModel` 都用 `isNotBlank()`。存储值非空但无法解析时，设置页摘要显示未登录，另外两处显示已登录。 | `SettingsScreen.kt:455`，`AccountsScreen.kt:163`，`LoginViewModel.kt:98-101` | 一致性 |
| U4 | 账号身份用 `isNetease` 分支：网易云取 `MainViewModel.userDetail` 的昵称和头像，其他平台取 `descriptor.accountName(stored)`。后者是同步函数，只能从凭证字符串里读，QQ 显示的是 musicid。 | `AccountsScreen.kt:155-165` | 结构 |
| U5 | 账号页不看 `qqEnabled`，QQ 关闭时仍显示 QQ 账号卡片。 | `AccountsScreen.kt:151` | 一致性 |
| U6 | 凭证状态不可观察：账号页靠 `reloadGeneration` 手动重读，设置页和 `LoginViewModel` 各持一份快照；`QQProvider` 续期写入后，三处都不会更新。 | `AccountsScreen.kt:97-106`，`SettingsScreen.kt:219-226`，`LoginViewModel.kt:41-45` | 结构 |
| U7 | 写凭证、退出、重命名和提示信息都在 composable 里完成，没有 ViewModel；登录页有 `LoginViewModel`。 | `AccountsScreen.kt:192-297` | 结构 |
| U8 | `SettingsScreen.kt`（1677 行）里的 `SettingsSectionLabel`、`SettingsTextField`、`AccountCard`、`SettingsLinkRow`、`SettingsExpanderRow` 改为 `internal` 供账号页使用，账号页因此依赖设置页文件。 | `SettingsScreen.kt:980`、`1029`、`1499`、`1586`、`1633` | 结构 |
| U9 | `LocalAccount` 放在 `data/auth`，本身不涉及认证；目前只有名称；`localAccountName` 不在备份里，是否应在备份里未定。 | `LocalAccount.kt`，`SettingsBackup.kt` | 结构 |

### 2.6 其他结构事实

- `MusicProvider` 的四个能力里，生产代码只用到 `search` 和 `streamUrl`（1.1）。`QQProvider.playlistDetail`（含分页）和 `QQProvider.lyric` 有测试，没有调用方。
- `ui/` 与 `viewmodel/` 下直接 import `data.api.dto` 的文件现在是 14 个，AGENTS.md D6（第 244 行）写的是 8 个：`NowPlayingUiState.kt`、`PlaybackBottomSheets.kt`、`TransportSheets.kt`、`AlbumScreen.kt`、`ArtistScreen.kt`、`HomeScreen.kt`、`NowPlayingScreen.kt`、`SearchScreen.kt`、`SharedScreenParts.kt`、`UserPlaylistScreen.kt`、`IntelligencePlayback.kt`、`MainViewModel.kt`、`NowPlayingViewModel.kt`、`SongRecognitionViewModel.kt`。
- `ProviderTrack` 映射时丢弃了 `QQSong.pay`（`pay_play`、`pay_month`）和 `QQSong.file`（各码率的文件大小），界面拿不到「需要 VIP」「有无损」这类信息（`QQProvider.kt:196-213`，`QQMusicApi.kt:169-212`）。
- `streamUrl` 的返回类型 `String?` 区分不了 P5 列出的几种失败。

### 2.7 已核对、未发现问题

- 混合平台队列的持久化与恢复：`PlayerStatus` 以 JSON 存 `String` id，恢复时按 id 重建占位 URI，过程中没有数值解析（`PlayerStatusRestorer.kt:61-73`，`Repository.kt:770-777`）。
- 播放上报对非网易云曲目显式排除（`PlaybackReportingCoordinator.kt:633`）。
- 离开登录页时取消 `LoginViewModel` 的作用域（`LoginScreen.kt:81-82`），扫码轮询随之停止。

---

## 3. 已记录项（D6「Still open」），本次确认仍成立

- QQ 结果不缓存（缓存表按数字 `song_id`）。
- 歌单页、「我的」页绑定网易云 DTO，分组设置只影响搜索；按平台分标签的资料库视图未实现；QQ 账号自己的歌单未拉取。
- QQ 曲目不能下载（下载队列按数字 id）；搜索结果行的菜单对 QQ 曲目只有「加入播放队列」（`SharedScreenParts.kt:151-178`）。
- QQ 歌词未接入歌词管线；QRC 未转换成 YRC。
- 登录账号在 MP3 128 以上能解锁什么，未测。

---

## 4. 修改建议

按依赖关系排列，不代表优先级。每条标出对应的发现。

### R1 平台能力模型（P1–P6、1.3）

在平台层声明能力，界面和 ViewModel 查询能力，替代各处解析 id：

```kotlin
enum class ProviderCapability {
    LYRIC, LIKE, COMMENTS, SONG_WIKI, CREDITS, DOWNLOAD, SHARE_LINK, PLAYBACK_REPORTING,
}

interface MusicProvider {
    val capabilities: Set<ProviderCapability>
    // …
}

fun MusicProviderRegistry.capabilitiesOf(mediaId: String): Set<ProviderCapability>
```

- ViewModel：不支持的功能写入单独的 `Unsupported` 状态，与 `Error` 分开，不再用英文内部信息占位。
- 界面：按能力隐藏或禁用入口，统一成一种处理方式（P3、P6 目前不同）。
- 较小的一步：只加一个 `MediaInfo.neteaseSongIdOrNull()`，把 1.3 中直接对 `MediaInfo.id` 调 `toLongOrNull()` 的 10 处（`NowPlayingViewModel` 9 处、`NowPlayingScreen.kt:535`）集中到一个可检索的函数，同时把 P1、P2 的英文信息换成「该平台暂不支持」一类的中文文案。这一步不改结构，只让判定位置可见。

### R2 取流结果带原因（P5）

```kotlin
sealed interface StreamResolution {
    data class Playable(val url: String) : StreamResolution
    data object NotEntitled : StreamResolution      // 例如 QQ 的 result 104003
    data object ProviderDisabled : StreamResolution
    data class Unreachable(val provider: MusicProviderId) : StreamResolution
}
```

`QQMusicApi.songUrl` 需要保留网关返回的 `result` 码（目前只取 `purl`）。播放器把原因交给界面；第 5 节 F4 的换源也以此为触发条件。

### R3 搜索按平台分页（S1、S5）

每个平台一个游标，替代共享 offset：

```kotlin
data class ProviderSearchCursor(
    val nextOffset: Int,
    val exhausted: Boolean,
    val lastFailed: Boolean,
)
```

「加载更多」只请求未到底的平台；上一页失败的平台重试它自己那一页；单个平台失败时提示是哪个平台。按平台分组视图可以给每组单独的重试入口。需要补的测试：一个平台第一页失败后加载更多；一个平台不足一页后加载更多。

S5：给网易云加与 QQ 相同的启用开关（默认开），`isAvailable()` 读它。这会改变「网易云总是参与聚合」的现有前提，列入第 6 节。

### R4 账号状态单一来源（U3、U4、U6、U7）

- `ProviderCredentialSlot` 暴露 `val credential: StateFlow<String>`，账号页、设置页摘要、`LoginViewModel`、`QQProvider` 都从这里读；续期写入后界面随之更新。
- 「已登录」由 slot 定义一次（例如 `val signedIn: StateFlow<Boolean>`），替代三种口径。
- 身份信息从 descriptor 的同步 lambda 移到可挂起的接口：网易云的实现读 `MainViewModel.userDetail` 或它的数据源；QQ 的实现调用网关的用户信息接口（该接口是否存在、需要什么参数，未核实）。账号页不再需要 `isNetease` 分支。
- 新增 `AccountsViewModel`，承接 `AccountsScreen` 里的写入、退出、重命名与提示。

### R5 平台配置集中到账号页（U1、U2、U5、T1）

每个平台一张卡片：启用开关、后端地址、账号、手动凭证。删除设置页的 QQ 段和两处后端地址输入，设置页的摘要按 descriptor 列表生成。后端地址的规范化只保留一个函数，由账号页和登录页共用（或登录页不再提供地址编辑）。账号页对未启用的平台显示「未启用」。

### R6 续期（C1–C4）

- 以凭证指纹为键记录「已检查」，凭证变化后重新检查。
- 写回前比较：`slot.write` 增加期望值参数（或提供 `compareAndSet(expected, new)`），当前值已经变了就放弃写回。
- 请求返回 401 时触发一次续期并重试原请求，同一凭证只重试一次。
- 补测试：过期 → 续期写回；续期期间凭证被清空 → 不写回；换凭证后再次检查。

### R7 小修

- `MusicProviderRegistry` 里的 `runCatching` 改成遇到 `CancellationException` 重新抛出（C7）。
- `QQQrLoginMethod` 的未应答计数移到 `QrLoginFlow` 或 `QrLoginSession`（C5）。
- `SettingsBackupData.qqEnabled` 改为 `Boolean?`，与 `libraryAggregationMode` 的约定一致（T2）。
- Web：按平台过滤 QQ 登录方法，或在账号页注明「Web 端以未登录身份访问 QQ 音乐」（C6；选哪种列入第 6 节）。
- 设置行组件移到独立文件（例如 `ui/component/SettingsRows.kt`），账号页和设置页都依赖它（U8）。
- `ProviderTrack` 增加可用性字段（例如 `requiresVip`、`bestAvailableQuality`），由 `QQSong.pay` / `QQSong.file` 映射（2.6），供第 5 节 F2 使用。

### R8 注册方式（1.2）

可选项：每个平台一个注册包，只登记一次，四个注册表从包派生。

```kotlin
class ProviderModule(
    val id: MusicProviderId,
    val provider: MusicProvider,
    val credentialSlot: ProviderCredentialSlot,
    val descriptor: ProviderLoginDescriptor,
    val loginMethods: List<LoginMethod>,
    val settings: ProviderSettingKeys,      // enabled / server / credential 键
)
```

- 得到：漏登记在构造时就不可能发生；设置页、账号页、备份可以按包遍历，1.2 的第 6–8 项不再逐平台手写。
- 代价：现有四个注册表的调用方都要改；只有两个平台时收益有限，接入第三个平台时才明显。
- 属于取舍，列入第 6 节。

### R9 文档

AGENTS.md D6 第 244 行的「eight files」与现状（14 个）不符；「Still open」没有写 2.1 的播放页表现。AGENTS.md 自己的规定是事实冲突时以代码为准、修改文档。

---

## 5. 特色功能方向

每项列出前置条件，以及它触及的已锁定约束或需要的决定。

| # | 功能 | 前置 | 触及的约束 / 需要的决定 |
|---|---|---|---|
| F1 | 正在播放页、迷你条、队列行显示曲目的来源平台（混合队列时可区分） | 无 | 无 |
| F2 | 搜索结果显示「VIP」「无损」标记；可选「优先显示当前账号能播放的来源」 | R7 的 `ProviderTrack` 可用性字段 | 标记只反映曲目属性，不代表当前账号的权益（`QQSongFile` 的注释已写明） |
| F3 | 合并视图里把跨平台的同一首歌合成一行，行内列出各平台来源，点击时按用户设定的平台顺序选源 | 标题、歌手、时长的归一化匹配 | 属于模糊匹配。AGENTS.md 对 TTML 的规定是不自动应用模糊标题匹配；若沿用这一原则，合并必须可见、可拆开、可改选 |
| F4 | 换源：某平台无法播放（VIP、下架、平台已关闭）时，提示「在另一平台播放同名歌曲」 | R2 | 同 F3 的模糊匹配问题；替换队列项必须走 `rebuildPlaylistFromTimeline()` 这一条路径；换成网易云曲目后是否上报网易云播放记录，需要决定 |
| F5 | 本地账号的歌单，允许混合各平台曲目；可从平台歌单导入 | 接入 `MusicProvider.playlistDetail`（已实现、未调用） | 新表从一开始就用 `(provider TEXT, raw_id TEXT)` 作键，不需要等现有 11 张缓存表的破坏性迁移；只有要关联那些缓存（例如复用歌单曲目缓存）时才依赖迁移。D6 的「Order of work」把 schema 与凭证迁移排在聚合界面之前，先做本地歌单等于调换这个顺序。本地歌单是否进备份，需要决定 |
| F6 | 与平台无关的本地「喜欢」，让心形按钮对所有平台的曲目都有效 | F5 的存储 | 与网易云账号「喜欢」的关系（只存本地，或同时写网易云）需要决定；QQ 网关有无收藏接口，未核实 |
| F7 | QQ 曲目使用 QQ 自己的歌词（同平台、精确 id，不涉及匹配） | 把 `QQProvider.lyric` 接入 `LyricResolver`；逐字歌词需要 QRC → YRC | 四态歌词来源策略目前只有 AMLL TTML 和网易云后端两个源。QQ 曲目在 `TTML_ONLY`、`BACKEND_ONLY` 下的含义需要定义；按现在的文字，`TTML_ONLY` 下 QQ 曲目没有歌词（AMLL DB 只收 ncm id） |
| F8 | 跨平台歌词回退（网易云曲目缺歌词时取 QQ 的，或反过来） | F7 | 需要重新设计歌词来源的隐私门，D6 已写明加一个枚举值不够；同时涉及 F3 的模糊匹配 |

---

## 6. 待决定

1. 能力模型：R1 完整版，还是只做集中判定的那一小步。
2. 注册方式：每平台一个注册包（R8），还是保留四个独立注册表。
3. 网易云是否可以关闭（S5、R3）；这改变「网易云总是参与聚合」的前提。
4. Web 端的 QQ 登录：隐藏、标注匿名，或维持现状（C6）。
5. 本地账号的数据（名称、将来的歌单、本地「喜欢」）是否进入设置备份（U9、F5、F6）；本地歌单是否先于 D6 的 schema 迁移实施（F5）。
6. 换源（F4）只允许用户确认，还是允许自动；换源后的播放是否上报网易云。
7. QQ 曲目在 `TTML_ONLY` / `BACKEND_ONLY` 下的歌词语义（F7）；是否做跨平台歌词（F8）。
8. 合并视图播放时，队列是否允许混合平台（S3，目前允许）。
9. 同一平台多个账号：D6 已锁定不做（"Multiple accounts of the *same* provider are explicitly out of scope"）。如要改变，需要先在 AGENTS.md 记录新决定；本文不含相关建议。

---

## 7. 处理状态（2026-09-23）

第 6 节的 9 项由用户委托决定，决定记录在 AGENTS.md D6「Decisions of 2026-09-23」。下表列每条发现的处理与所在提交。已编译 Android / Desktop / Web / iOS 源码，`:shared:jvmTest` 与 `:shared:wasmJsBrowserTest` 通过；未在真机运行界面。

### 7.1 待决定项的决定

| # | 决定 | 实施位置 |
|---|---|---|
| 1 | 采用完整能力模型（R1） | `5f9f76d` |
| 2 | 每平台一个 `ProviderRegistration`，四个注册表由它派生（R8） | `664959e` |
| 3 | 网易云不可关闭；改为按平台报告和重试搜索失败。启动时仅在所有已启用平台都未登录时才打开网易云登录页 | `7a20e40`、`664959e` |
| 4 | Web 端不提供 QQ 登录，账号页说明原因，且从不发送 `Cookie` 头 | `664959e` |
| 5 | 本地账号名称与本地歌单进入备份；本地歌单先于 D6 的缓存表迁移实施 | `664959e`、`2b6dd57` |
| 6 | 换源仅由用户确认，随机播放时不提供；换成网易云曲目后按网易云曲目上报 | `018279f` |
| 7 | QQ 曲目的「后端」歌词 = QQ 网关；`TTML_ONLY` 下 QQ 曲目不发任何请求并说明原因；不做跨平台歌词回退 | `b24be31` |
| 8 | 合并视图允许混合平台的队列 | `5f9f76d`（标记）、`7a20e40`（合并同曲） |
| 9 | 同平台多账号维持 D6 锁定，不实施 | — |

### 7.2 发现的处理

| 发现 | 处理 | 提交 |
|---|---|---|
| P1–P4、P6 | 能力模型；歌词、歌曲详情为 `Unsupported` 状态，评论、歌手菜单按能力禁用；心形改为写入本地喜欢 | `5f9f76d`、`2b6dd57` |
| P5 | `StreamResolution` + 注册表旁路通道；播放失败原因以提示条显示 | `5f9f76d` |
| S1、S5 | 每平台游标；失败平台重试原页；部分失败行可重试 | `7a20e40` |
| S2 | 合并视图折叠同曲（严格匹配），可在账号页关闭 | `7a20e40` |
| S3 | 保持允许（决定 8），来源平台标记 | `5f9f76d` |
| S4 | 未处理：搜索建议与热搜仍只来自网易云（QQ 网关的对应接口未核对） | — |
| C1–C4 | `QQCredentialRenewer`：按凭证检查、401 时续期一次并重试、比对写回；有测试 | `5b19a38` |
| C5 | 未应答计数移入 `QrLoginFlow` | `5b19a38` |
| C6 | 见决定 4 | `664959e` |
| C7 | 注册表改用放行取消的 `providerCall` | `5b19a38` |
| T1 | 地址统一由 `ProviderServerSetting.normalize` 处理 | `664959e` |
| T2 | `qqEnabled` 改为可空 | `5b19a38` |
| U1–U9 | 账号页由注册表驱动，集中平台开关、地址（含检查）、账号、凭证；`AccountsViewModel`；凭证槽可观察并统一「已登录」判定；QQ 账号名取自网关主页；设置行移至 `SettingsRows.kt`；`LocalAccount` 移至 `data/local` | `664959e` |
| 2.6 平台接口 | `lyric` 接入歌词管线；`playlistDetail` 用于导入平台歌单 | `b24be31`、`2b6dd57` |
| 2.6 DTO 文件数 | AGENTS.md 更新为 14；未减少（资料库页面的中性模型仍属 D6 未完成项） | 文档提交 |
| 2.6 可用性字段 | `ProviderTrack.tags`（VIP / 付费 / 无损） | `7a20e40` |
| F1–F7 | 均已实施 | 见上 |
| F8 | 不实施（决定 7） | — |

### 7.3 实施中对本报告的更正

- 1.3 所列 `NowPlayingViewModel` 的 id 解析实为 11 处：收藏状态同步里的 `mediaId?.toLongOrNull()` 未被原检索式匹配到；动态封面同样是网易云专属功能，已加入能力 `DYNAMIC_COVER`。
- 决定 5 原文为「按 `(provider TEXT, raw_id TEXT)` 建表」。Web 端 SQL 驱动只支持单键 JSON 表，实施为一个 JSON 文档表，曲目在文档内按 `(provider, rawId)` 标识；AGENTS.md 已改写该条。
- 修改歌词管线时发现 `LyricResolver.kt` 中三条错误文案有字符损坏（编码往返所致），已恢复为「现有歌词后端请求失败」「AMLL DB 查询超时」「AMLL DB 的 TTML 无法解析」。

### 7.4 验证范围

- 已验证：各平台编译；JVM 与浏览器单元测试；对本机 QQ 网关的只读请求——账号主页接口的返回结构、两首真实曲目的 QRC 转换（各 63 行逐字歌词）。
- 未验证：Android / iOS / 桌面 / Web 上的界面运行；QQ 账号登录后的续期与 401 重试（无可用账号）；Web 端网关的跨域；换源在真实失败曲目上的端到端表现。
