# 生产就绪审查记录

审查基线：`19712b6`（2026-10-03）。本记录延续此前审查，目标尚未完成。旧工作流的 `confirmed` 数组混有未复核项；下表不会把这些候选当作已确认事实。

## 第一批代码审查

旧工作流读取了播放核心、数据/API、账号认证三个分区；持久化/设置分区中断，后续分区尚未完成。报告严重度为原报告的分级，须结合复核结论阅读。

| 编号 | 分区 | 报告级别 | 候选问题 | 当前状态 |
|---|---|---|---|---|
| W1-01 | data-api | P0 | Android 上保存的网易云服务器地址不生效：API 客户端在设置载入前就用默认地址建好，会话 Cookie 被发往默认服务器 | 已核实并修复：只有队列恢复器立即创建；播放器、报告与衔接协调器在设置加载后于主线程创建。Koin 实际装配及延迟/失败加载回归测试通过。 |
| W1-02 | data-api | P1 | Web 存储驱动配额不足时按大小删除持久化行，会删掉本地歌单库、保存的播放队列和下载队列 | 已核实并修复：只驱逐 Cached*；歌单和队列写失败保留旧值并报错，事务回滚不重写无关用户数据。5 项浏览器配额/权限测试通过。 |
| W1-03 | data-api | P1 | 账号歌单只取第一页：超过 30 个的歌单在应用里看不到 | 已核实并修复：按 offset/more 拉取全部歌单后合并缓存；中间页失败不覆盖旧缓存，空页或重复页终止；定向 JVM 测试通过，最终跨平台校验另存结果。 |
| W1-04 | auth-accounts | P2 | QQ 凭证的主动续期永远不会触发：/login/check_expired 的应答总被读成 null | 已核实并修复：按本地 ba95861 网关的 bool 信封读取过期状态；匿名传输不发检查或续期请求，单次请求固定凭证快照。真实响应形状及 5xx/匿名回归测试通过。 |
| W1-05 | auth-accounts | P2 | 扫码登录在过期以外的终止情况下停止轮询，旧二维码却仍可扫，下方只剩“取消” | 已修复并测试：非过期终止清除二维码并恢复生成入口；取消后的生成/保存结果不能覆盖新一轮状态。 |
| W1-06 | auth-accounts | P2 | 网易云手动粘贴的 Cookie 不做校验：值里有换行时所有网易云请求都失败，扫码页还会明文显示整段 Cookie | 已修复并测试：拒绝控制字符和非法 Cookie 名，清理响应属性；扫码与短信的传输/保存异常不显示原始请求内容。 |
| W1-07 | auth-accounts | P2 | 手机验证码登录的号码栏在手机宽度下被英文按钮挤到约 40–80dp，无法正常输入 | 已修复：窄屏或大字号时号码与发送按钮改为上下布局，输入框不再被按钮挤压。编译通过，视觉回归待补。 |
| W1-08 | auth-accounts | P2 | 导入歌单时把任何 163.com 链接里的 id= 当成歌单 id，超长 id 会让 toLong() 抛异常 | 已修复并测试：按精确服务域名及歌单路由解析，歌曲/专辑/伪造域名不再被识别为歌单；超长 ID 安全返回失败。 |
| W1-09 | data-api | P2 | 检查网易云服务器地址时，会把已存的会话 Cookie 发给这个尚未保存的地址 | 已核实并修复：服务器检查显式禁用凭证；请求头和查询参数两种传输的本地 HTTP 测试通过。 |
| W1-10 | data-api | P2 | 部署在子路径下的网易云服务器：检查通过，但所有 API 请求都丢掉了路径 | 已修复并测试：NCM 端点使用相对路径，服务器路径按目录保留；本地 HTTP 测试覆盖带/不带末尾斜杠的子路径以及两种 Cookie 传输。 |
| W1-11 | data-api | P2 | 不带 http:// 的服务器地址会被保存，之后该服务的所有请求都失败，只显示笼统的「无法连接」 | 已修复并测试：登录页、账号页和备份导入统一验证绝对 HTTP(S) 地址、显式主机及合法端口，拒绝查询参数/片段；无效备份在任何写入前失败。 |
| W1-12 | data-api | P2 | 歌词、评论、歌单等缓存无限增长，且没有任何清除途径 | 已核实并修复：九类文本缓存共享 128 MiB 默认上限及 4096 条上限；设置页可调容量、查看用量或清理。SQLite/Web 同一组测试验证驱逐与用户数据隔离，备份缺字段保留当前值。视觉回归待补。 |
| W1-13 | player-core | P2 | Android 在移除或拖动队列项后切歌不推进 playbackOccurrence，上报协调器因此冻结旧会话，新曲目完全不上报 | 已修复：以 Media3 window UID 判断新选曲，显式替换和纯元数据更新去重；删除当前项会计入新选曲。Android Host 分支测试通过，原生事件顺序仍需设备回归。 |
| W1-14 | player-core | P2 | 随机模式下的队列在进程重启恢复后丢失原始顺序，关闭随机只能回到上次的随机顺序 | 已修复并测试：保存原始队列、索引及排列，五种播放器直接恢复暂停状态；重复歌曲保持独立位置。旧文件缺少原始顺序时保留其已有顺序，不声称能补回缺失信息。 |
| W1-15 | player-core | P2 | 桌面、iOS、Web 的 setQueue 会静默关闭随机播放，Android 则保持随机，四端行为不一致 | 已修复并测试：替换非空队列保留已有随机模式，Desktop/iOS/Web/参考播放器与 Android 对齐。 |
| W1-16 | auth-accounts | P3 | QQ 凭证输入框的说明文字在 DI 创建时就取了值，切换语言后不再更新 | 已修复并测试：QQ 凭证说明按当前语言读取，不在方法创建时冻结。 |
| W1-17 | auth-accounts | P3 | 网关返回的凭证字段是 encrypt_uin，应用按 encryptUin 读取，扫码或短信登录后账号页显示不出 QQ 昵称 | 已核实并修复：encrypt_uin/login_type 使用网关字段名，同时兼容粘贴 SDK JSON 的 camelCase。字段往返测试通过。 |
| W1-18 | auth-accounts | P3 | 登录页自己留了一份凭证副本，不跟随 credentialUpdates()，后台续期后点“保存”会把旧凭证写回去 | 已修复并测试：登录页订阅 credentialUpdates；未编辑的输入跟随续期，正在编辑的内容保留，后台变更不触发登录页自动结束。 |
| W1-19 | data-api | P3 | 缓存写入失败会丢掉已拿到的网络结果，并清空整个账号界面、显示原始异常文本 | 已修复并测试：共用缓存流、用户等级和后端歌词的展示不因缓存写失败而丢弃网络结果；保留等级请求顺序，显式离线歌词缓存失败仍返回 Failed，用户数据写入也仍须成功。 |
| W1-20 | data-api | P3 | Web 上每个网易云 API 请求前都有一次永远命中不了缓存的 CORS 预检 | 已修复并测试：取消全局 JSON Content-Type；无正文的 GET/POST 不再因此触发预检，真正 JSON 正文仍显式发送正确类型。请求头与 JSON 正文用本地 HTTP 测试验证。 |
| W1-21 | player-core | P3 | 清空队列或移除到空后再「加入播放队列」，正在播放页仍显示旧的「来自 …」来源 | 已修复并测试：发布空队列时同步清掉 PlaybackSource，之后单曲加入不继承旧来源。 |
| W1-22 | player-core | P3 | 同一次选择在 ERROR 或 IDLE 之后恢复播放会被当成新会话，重复发送 startplay 并启用新的 relay sessionId | 已修复并测试：同一 occurrence 的 ERROR/IDLE 保留会话 ID、startplay/scrobble 去重和累计听歌进度；选曲、清空或换账号仍结束旧会话。 |
| W1-23 | player-core | P3 | 桌面端网络卡顿时位置停滞但状态仍为 PLAYING，随后的前向拖动会被计为收听时间 | 已修复并测试：播放器在显式 seek 前发布独立的进度跳变标记，Android 系统媒体控制也覆盖；上报排除拖动距离，不把停滞期间的墙钟时间算作有效收听。 |

## 此前提交的回归复审

| 编号 | 问题 | 当前状态 |
|---|---|---|
| R-1 | Release 合并同名 mapping.txt | 已核实并修复：Release 和通知作业仅合并五类安装包，映射和验证 APK 保留为独立 CI artifact。YAML 解析通过；未打标签或发布。 |
| R-2 | 跨版本备份省略音质值，导入后改变音质 | 已核实并修复：新导出明确写出两个音质值；旧备份缺字段时保留导入设备当前值，避免推测旧值。跨格式回归测试通过。 |
| R-3 | Android 启动窗口未跟随应用强制深浅色 | 已实现并编译验证：Android 12+ 从已落盘的主题设置同步系统应用级夜间模式，避免重建取消未保存的设置；旧版 Android 在 DataStore 就绪后立即匹配 Activity 背景。首次升级和旧版系统启动窗口仍有平台边界，真机冷启动回归待补。 |
| R-4 | 全局主题变化清掉页面已提取的封面色 | 已按源码核实并修复：提取色仅随图片身份失效，默认主题色仅在未提取时使用。各目标编译通过；实际切歌界面回归待补。 |
| R-5 | 许可证缩进段落仍保留硬换行 | 已核实并修复：缩进不再阻止续行合并，列表标记仍保留。使用仓库真实 Apache/AGPL 全文的两项排版测试通过。 |
| R-6 | 许可证页返回设置页丢失滚动位置 | 已按源码核实并修复：加载/错误面板使用独立滚动状态，不压缩已保存的位置。各目标编译通过；导航界面回归待补。 |

## 验证边界

- 通过编译不代表在设备上运行通过；Windows 无法验证 iOS framework 链接或 iOS/macOS 交互。
- 测试使用隔离数据，不接触真实账号、歌单或播放队列。
- AMLL 子模块有既有本地提交，主仓库指针尚未更新；任务全部完成后再统一推送，不创建标签或发布版本。

## 2026-10-03 本轮验证

- `:shared:jvmTest`：591 项，0 失败、0 跳过。
- `:desktopApp:test`：15 项，0 失败、0 跳过。
- `:shared:wasmJsBrowserTest`：451 项，0 失败、0 跳过，本机 Edge 运行。
- `:shared:compileAndroidMain`、`:androidApp:compileDebugKotlin`、`:shared:compileKotlinIosSimulatorArm64` 通过。
- `scripts/check_tracked_secrets.py --self-test`、`git diff --check`、CI YAML 解析通过。
- 本轮没有运行 Android/iOS 真机或重新做桌面视觉回归，没有构建安装包、推送或发布。

上述第一批代码问题已处理；后续重点为平台运行时回归、下载文件处理及其余尚未覆盖的分区。

## 后台验证方式（2026-10-03 新指令）

- 不再通过模型调用 write_stdin、等待工具、定时任务或读取日志来反复轮询。
- 本机 CLI 不支持 `codex queue`；`codex app-server daemon version` 提示 daemon 管理仅支持 Unix，当前桌面会话未暴露可用连接地址。本轮无法可靠配置一次性唤醒。
- 最终跨平台检查由独立隐藏 PowerShell 进程一次执行；退出时写入 `build/audit-review/final-check-result.json` 和 `build/audit-review/final-check.log`。回调未配置，不承诺自动汇报。
- 本文前述 591/15/451 统计对应歌单分页修复之前的完整测试；分页和缓存容错已有定向 JVM 验证，最终完整结果以退出报告为准。

## 登录与队列修复验证（2026-10-03）

- 上轮 `3990ee3` 的退出报告随后已落盘：JVM 596、桌面 15、Web 453 项均通过。进程消失时尚未看到报告，不等于构建失败。
- `auth-check-result.json`：JVM 605、桌面 15、Web 455 项通过；Android/iOS Kotlin 编译通过。
- `queue-check-result.json`：JVM 610、桌面 15、Android Host 448、Web 459 项通过；全部 0 失败、0 跳过。Android/iOS Kotlin 编译通过。
- 两份本轮报告的 commit 字段是验证时的 HEAD；报告覆盖的是随后提交的工作区修改，测试期间没有运行真实账号。
- 新发现并修复：清空队列之前会跳过保存，重启可恢复旧队列；现在成功恢复后也保存空快照，恢复失败时仍保留磁盘数据。
- 新发现并修复：离开短信登录方式时在途授权仍可能落地；现在取消请求，防止迟到的应答保存凭证，并同步防重发和更换号码后的旧验证码复用。

## 服务器、缓存与 Android 生命周期验证（2026-10-03）

- `round-complete-result.json`：JVM 615、桌面 15、Android Host 452、Web 462 项全部通过；Android/iOS Kotlin 编译通过。
- 早期失败已定位并修复：Ktor 扩展属性导入缺失；Ktor 将缺失主机的 https:// 补成 localhost；备份导入不能无故改写合法地址的原有拼写；Android 外壳不直接依赖 Material3，背景色改由 shared 返回 Int。
- API 31+ 的系统启动主题方案依据 [Android 文档](https://developer.android.com/develop/ui/views/theming/darktheme)；AUTO 清除应用覆盖的行为已对照 [UiModeManagerService](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/services/core/java/com/android/server/UiModeManagerService.java) 的 setApplicationNightMode 实现核对。

## 第二批：下载检查已开始

| 编号 | 问题 | 当前状态 |
|---|---|---|
| W2-01 | Android dataSync 前台服务未处理系统超时，可能触发进程异常 | 已按源码和 [平台规则](https://developer.android.com/develop/background-work/services/fgs/timeout) 核实；实现 onTimeout，取消服务观察后同步暂停任务并停止服务。编译通过，设备超时注入待补。 |
| W2-02 | 超长日志行分段后，URL 查询参数尾部失去前缀而绕过脱敏 | 回归测试在旧实现失败，修复后通过：超过 16 KiB 的整行以固定提示替代，直到换行前持续丢弃文件输出；控制台输出保留。 |
| W2-03 | 日志轮转失败或直接写入超大块时仍会突破文件上限 | 已修复并测试：超大块不写入，轮转受阻时保留旧文件并丢弃新增文件输出，解除阻塞后可继续轮转。 |
| W2-04 | 账号页直接显示凭证保存/退出异常，超长 JVM Preferences 值会带出原始 Cookie | 两个回归测试在旧实现失败，修复后通过：使用固定本地化错误，不展示存储异常；原有凭证保留。 |
| W2-05 | 桌面下载在部分 JDK 上把截断响应当成成功，各平台还可能发布空歌曲 | 已核实并修复：桌面明确检查 HTTP 状态、非空内容和响应声明长度；Web 检查非空内容，身份编码且头可见时校验长度；iOS 检查状态及临时文件大小。JVM 本地 HTTP 和 Web OPFS 测试通过，iOS 仅编译验证。 |
| W2-06 | Web 拒绝一次下载发布时可能删掉已有音频，替换下载失败也会丢失旧文件 | 浏览器回归在旧实现失败；修复后在 OPFS 发布前取得许可，替换失败保留旧文件，拒绝新下载不留下最终文件。三个浏览器回归通过。 |
| W2-07 | 缓存容量函数的可空挂起调用触发桌面 ProGuard 优化异常，发布构建失败 | 发布构建复现 dup2_x1 栈分析异常；改用等价显式分支后 JVM/桌面测试和 proguardReleaseJars 通过，保留混淆与优化。 |

本次阅读了 AndroidDownloadService、PersistedDownloadQueue 及 SongDownloadManager 的控制/恢复相关路径；下载编排其余部分、各平台文件处理和其余审查分区尚未完成。

## 播放上报边界验证（2026-10-03）

`reporting-check-result.json`：JVM 618、桌面 15、Android Host 455、Web 465 项均通过，Android/iOS Kotlin 编译通过。新增测试覆盖错误重试去重、停滞后 seek、参考播放器 seek 与选曲事件的分离。未向真实账号发送播放上报。

## 文本缓存容量验证（2026-10-04）

- `cache-settings-check-result.json`：JVM 625、桌面 15、Android Host 457、Web 472 项均通过；Android/iOS Kotlin 编译通过。
- 缓存统计为 UTF-8 JSON 内容字节数，清理不执行 VACUUM，不声称 SQLite 文件立即缩小；设置页三语说明这一边界。浏览器配额可能小于配置容量。
- 驱逐按本进程访问顺序进行，旧缓存没有时间戳，初次从查询顺序开始；超大单条响应不进入缓存，但网络展示继续。
- 本地歌单/收藏、播放队列、下载队列和音频文件不参与清理。SQLite 与浏览器驱动都验证了这条隔离约束。
- 新增容量备份字段为 nullable，旧备份不改变当前值；非法上限在写入任何设置之前被拒绝。

## 日志与账号错误隐私验证（2026-10-04）

`privacy-check-result.json`：JVM 627、桌面 18、Android Host 457、Web 472 项均通过，Android/iOS Kotlin 编译通过。`privacy-repro` 和 `privacy-account-repro` 保存了修复前失败结果。凭证测试只使用随机命名的临时 Preferences 节点和模拟密钥。

## 下载完整性与发布验证（2026-10-04）

- 同一本机 HTTP 探针声明 10 字节却只发送 3 字节：JDK 25 抛 Premature EOF，JDK 21 读完返回 3 字节。因此完整性不能依赖运行时恰好拒绝截断。
- `download-publish-check-result.json`：JVM 632、桌面 18、Android Host 457、Web 476 项通过；Android/iOS Kotlin 编译通过。脚本使用本机 JDK 21。
- JVM 的实际下载函数使用临时目录和回环 HTTP 服务，覆盖截断、空响应、非请求的部分响应、chunked 响应、旧空文件恢复及拒绝发布。API 的建议大小只用于进度，不作为完整性的权威依据。
- Web 检查实际 OPFS 文件；旧实现误删已有文件的失败结果在 `web-publish-repro` 中。浏览器可能解压响应，CORS 也可能隐藏 Content-Encoding，因此不能盲目拿 Content-Length 与解码后的大小比较。
- iOS 的状态和临时文件检查只完成 Kotlin 编译；后台会话和 ActivityKit 的设备行为仍待验证。

## 发布构建与界面回归（2026-10-04）

- `release-check-result.json` 对应主仓库 `20926cd`、AMLL `7db5a66`：Android unsigned Release APK 和 Web production distribution 构建成功；桌面 ProGuard 在 MetadataCache.budget 失败。失败时遗留的 mapping 文件不能当作成功凭证。
- 修复容量函数后，`desktop-release-repair-result.json` 的三个实际任务（JVM 测试、桌面测试、ProGuard）通过；该报告列出的 Android/Web 测试计数来自上一轮 XML，本轮没有重新运行这些任务。
- 未混淆桌面实例使用独立 user.home 和内存 Preferences，静音且不持有账号；已确认能启动。桌面可访问性树只暴露窗格，未用真实鼠标代替控件操作。
- Web 使用新随机本地端口的独立存储，验证匿名首页、文本缓存设置、390×844 窄屏布局；从许可证页返回后保持原滚动位置（R-6 的 Web 路径已运行验证）。截图保存在 `build/audit-review/cache-narrow.jpg`。
- 原生平台交互、桌面混淆后运行和各系统安装包仍需继续验证。识曲分区新候选：关闭页面时恢复播放没有绑定原来的选曲；尚未加入已确认问题表。

## 识曲恢复播放验证（2026-10-04）

- 关闭识曲页会恢复另一处新选中的暂停歌曲，已由 `recognition-repro` 复现。恢复现在同时匹配 media ID 和 playbackOccurrence，重新选择同一首歌也不会触发旧恢复请求。
- 测试同时覆盖原曲保持不变时恢复、原本暂停时不恢复，以及一次恢复请求不会被重复消费。
- `recognition-check-result.json`：JVM 634、桌面 18、Android Host 459、Web 478 项通过；Android/iOS Kotlin 编译通过。
- 混淆后的桌面 JAR 已启动并创建窗口，日志能确认初始化与请求路径，持久化日志的 URL 查询参数已遮蔽；用户按 Escape 停止了截图自动化，因此未确认混淆后的视觉呈现。隔离实例已退出。

## 第三批：识曲与智能过渡

| 编号 | 问题 | 当前状态 |
|---|---|---|
| W3-01 | 关闭识曲页面会播放另一处新选中的暂停歌曲 | 已修复并通过跨平台测试，见上节。 |
| W3-02 | 智能分析在最后一个等待者取消后仍运行，快速切歌可启动无上限解码，失败 ID 也一直保留 | 三项回归在旧实现失败。现按等待者计数取消无主任务；最多并行分析两首歌；失败缓存与成功缓存均有 48 条上限。共享等待者仍可完成并复用结果。 |
| W3-03 | Web 取消分析只取消 Kotlin 等待，没有中止 fetch，且为整首歌曲额外分配 mono 副本 | fetch 回归在旧实现失败。现发出 abort，并等待不能取消的原生解码结束后再释放并发槽位；只转换、保留首尾片段。真实 WAV 解码测试验证内容、长度和时间轴。 |
| W3-04 | ONNX 会话创建成功但预热推理失败时没有关闭原生会话 | 已按资源所有权路径修复：尝试下一个设备前关闭失败会话。编译及普通测试通过，未在真实故障驱动上注入失败。 |

- `analysis-final-check-result.json`：JVM 638、桌面 18、Android Host 463、Web 484 项通过；Android/iOS Kotlin 编译通过。
- Desktop 分析解码每读取至多一秒 PCM 检查一次取消；正在执行的原生网络读取仍受其传输超时约束，不声称能即时中断阻塞的 FFmpeg 调用。
- `SmartTransitionHarnessTest` 曾在没有真实音频参数时直接返回，因此此前的 JVM 计数不能证明真实音频或 NPU/GPU 路径已测试；现已改为显式跳过。显式配置却缺少输入或输出目录时会失败，不再静默通过。
- Desktop 相邻相同 media ID 的交接问题已由合成音频及实际预取路径复现并修复，见下节。

## 字体许可与测试报告边界（2026-10-04）

- Web 打包了 Noto Sans SC variable 字体。实际字体元数据为 `2.004-H2`，声明 SIL OFL 1.1 及 Adobe 2014–2021 的版权信息；许可全文原先没有随应用提供。
- 已对照 [Google Fonts 的该字体许可](https://github.com/google/fonts/blob/main/ofl/notosanssc/OFL.txt) 补充完整文本、根声明和应用资源副本，并增加三语的字体许可入口。没有改动字体二进制或应用自身的许可证。
- 新测试比较应用内声明及三份许可文本与根目录原文，防止维护时仅改一处。
- `license-harness-check-result.json`：JVM 639 项中 **636 通过、3 明确跳过**；桌面 18、Android Host 463、Web 484 项全部通过。Android/iOS Kotlin 编译通过。跳过的三项都需要主动提供真实音频/加速器测试参数。
- 远端已重新 fetch：本地主仓库没有落后 `origin/main`，尚未推送。审查与原生设备/安装包验证仍未完成。

## 桌面链式过渡与播放线程（2026-10-04）

| 编号 | 问题 | 当前状态 |
|---|---|---|
| W3-05 | 三首连续播放时，第一段尚未淡出完毕，第二段计划被旧主音轨挡住 | 旧实现回归失败；新计划按已听到的解码实例接收，当前淡出仍独立完成。三首合成音频经过真实 FFmpeg 解码、混音和播放器队列路径通过。 |
| W3-06 | 相邻同 ID 歌曲无法推进队列，重复计划还会被预取去重标记拦截 | 时钟、计划、预取均绑定本次播放的解码实例，协调器以队列索引区分重复项。扩大到实际预取路径后再次复现并修复；三次同 ID 播放完成两次交接。 |
| W3-07 | 已取消的播放线程仍打开 URL，同 ID 的旧时钟还能更新新会话位置 | 加入会话检查；本地 HTTP 回归证明取消会话不产生请求，旧时钟不能写入当前位置。 |
| W3-08 | 停止调用同步关闭仍由工作线程使用的解码器，可阻塞控制线程 | 受控阻塞回归在旧实现失败；现在由播放线程 finally 回收，停止调用可先返回。底层网络读取仍受原有超时约束。 |
| W3-09 | 识曲暂停正逢音频交接时，恢复标记可能记住暂停前的旧选曲 | 恢复标记改在 pause 完成后读取，回归覆盖暂停过程同步新选曲的情况。 |

- 准备完成的解码器以原子操作转交，迟到或被替换的结果只由当前持有方关闭。
- 过渡中的暂停会丢弃已缓冲的混音，恢复时从真正听到的当前歌曲和位置重新开流。自然播放结束前也发布最后一次时钟读数，避免丢失交接。
- `playback-lifecycle-full-check-result.json`：JVM 646 项中 **643 通过、3 跳过**；桌面 18、Android Host 464、Web 485 项通过。Android/iOS Kotlin 编译通过。
- `transition-queue-repro`、`transition-prefetch-repro`、`decoder-lifecycle-repro` 分别保存了修复前失败结果。合成音频测试不开启真实扬声器，未使用用户歌曲或账号。

## Windows 安装包检查（2026-10-04）

`msi-check-result.json`：主仓库 `4fb9446`、AMLL `7db5a66` 构建出 `RedefineNCM-1.0.411.msi`。只读 MSI 数据库核对 ProductVersion、191 个文件条目、应用 JAR 及 OFL 许可均通过；未执行安装。这个安装包早于上节链式过渡修复，最终发布检查仍须覆盖最终提交。

## 修复后的混淆构建与本机加速器探针（2026-10-04）

- `desktop-runtime-check-result.json`：生产代码快照 `a64f15e` 的 `proguardReleaseJars` 通过，产物为 `desktopApp-v0.0.4.a64f15ed.jar`。
- 单独运行了 `SmartTransitionHarnessTest.beatModelRunsOnAnAccelerator`：1 项通过、0 跳过。本机选中 **AMD Radeon RX 7900 XT / DirectML**，连续 8 次零输入推理的输出形状及数值有限性检查通过。
- 本次记录的平均值为每个 15 秒模型片段 19 ms；它只衡量推理调用，不含下载、解码、频谱计算或模型首次加载。
- 此探针通过专用空目录标记开启既有 harness 开关，只执行零张量测试，使用仓库模型；没有读取用户音乐或账号数据。此前默认套件跳过的 GPU 探针现已有独立结果，真实音乐的两项 harness 仍未执行。
- 后续隐私候选待复核：后端地址允许 URL user-info，而现有日志和备份仅显式排除 Cookie 等字段；需要验证嵌入地址的用户名/密码是否会进入日志或共享备份。
