# 原生网易云协议层

## 目标与范围

Android、iOS、Desktop/JVM 直接使用 Kotlin/KMP 协议层连接网易云，不部署或启动
NeteaseCloudMusicApi 服务，不内嵌 Node.js、JavaScript 引擎或 WebView，不监听 localhost 端口。
播放器仍从返回的 CDN URL 获取音频；本次迁移不改变音频解码、队列、缓存结构或歌词来源策略。

协议参考固定为
[NeteaseCloudMusicApiEnhanced/api-enhanced `4a7d557326d16d2f1e5172ee69c9471eb3a50173`](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/tree/4a7d557326d16d2f1e5172ee69c9471eb3a50173)。
该提交的 package version 是 4.41.1，但不等于同版本 npm 包、发布标签或将来的 main 源码。
只移植 `NCMApi` 实际使用的接口，不声称兼容上游全部服务器功能或插件。

Web/WASM 仍使用设置中的 HTTP API 服务。浏览器的 CORS、Cookie/User-Agent 等请求头限制
不会因协议被 Kotlin/WASM 重写而消失。QQ Music 使用另一套服务，此次不迁移它。

## 分层

- `NCMApi`：保留既有方法和 DTO，避免重写 Repository、登录流程和播放器。
- `NativeNcmEngine`：Ktor 的进程内兼容边界，将既有调用分派给原生层，返回相同 JSON。
  `native-ncm.invalid` 是内部寻址标签，不会向该域名发出网络请求。
- `NcmEndpointMapper`：路由、字段、加密类型和模块级响应转换的白名单；未知路由失败，不猜测透传。
- `NcmProtocolClient`：Cookie、设备标识、匿名初始化、XEAPI 公钥注册、请求封包与响应解密。
- `NcmWire`：真实 Ktor 网络请求。只允许固定网易云 HTTPS 主机；不跟随带凭证的重定向，
  不记录请求/响应正文、Cookie 或带令牌的播放地址；响应字节数有上限。
- `NcmCrypto` / `NcmProtocolCrypto`：密码原语与上游的 WEAPI/EAPI/XEAPI 封包分离。
- `NcmNcblBuilder`：原生 NCBL v3 上报封包，使用上游支持的 gzip fallback；PLV 成功后才发送 PLD。

Android/JVM 使用 cryptography-kotlin 的显式 JDK/Bouncy Castle provider，不修改全局安全
provider 列表；iOS 使用同库的预编译 OpenSSL 3 provider。NCBL 的原始 ChaCha20 和模幂使用
Bouncy Castle/JDK BigInteger 或 OpenSSL EVP/BN。没有自行实现 AES/RSA/X25519 等密码原语。
这些历史协议中 MD5、ECB、无填充 RSA 和重复帧流密码状态仅用于线格式兼容，不能用于新安全协议。

二维码在本地生成 PNG，无需服务器或第三方二维码网站。

Android R8 和 Desktop ProGuard 保留 BC 的 JCA 映射/算法反射边界。Desktop 合并为单个
优化 JAR 时，构建任务移除依赖 JAR 遗留的 `.SF`/`.RSA` 等签名元数据：合并已改变原签名
覆盖的内容，保留它们会触发 JVM `Invalid signature file digest`，不是有效的供应链认证。
此处理只作用于生成的发布 JAR，不修改下载依赖，不关闭 JVM 校验或网络 TLS 验证。

## 账号与兼容

原有 `cookie`、UID 绑定、本地收藏、SQLDelight 缓存、下载队列保持原键和结构，不清空数据。
原生端忽略旧的 `server` 设置并隐藏其编辑入口；保留该值供 Web 设置备份兼容，不在迁移时删除。

安装私有状态单独存储：`ncmNativeDeviceId`、`ncmNativeAnonymousCookie`、`ncmNativeXeapiPublicKey`。
不把它们加入共享设置导出。设备 ID 在首次使用前生成；XEAPI 公钥注册签名经校验；匿名登录
必须先具备公钥。每进程首次使用重新注册公钥，网络失败时只允许使用保存不足 24 小时且结构
有效的旧密钥。状态按需初始化，不阻塞本地缓存的初次显示。

凭证每请求绑定，播放上报继续使用协调器传入的 Cookie 快照。传输会话按凭证隔离，
原生层不通过更新 Cookie 的方式替换用户账号，也不把临时上报 `os` 值写回设置。
取流 URL 仍不持久化。

业务写入和上报不自动重试，Android/JVM 的专用 OkHttp 连接也关闭透明连接失败重试；
PLV 已被接收而 PLD 失败不能伪装成成功或换端点重报。HTTP 压缩只协商可限量解码的 gzip。
外层 API client 关闭会连带释放原生 engine 与真实网络连接池，取消也会传播到内部请求。
迁移不授予任何会员、地区或版权权益，也不包含上游的可选解灰/代理插件。

## 验证方式

离线测试覆盖固定随机源密码向量、路由/字段类型/响应形状、gzip 上限、NCBL 帧和 HTTP 分派。
密码向量使用 Node 内置成熟密码实现及固定上游源码生成；这些脚本仅用于开发验证，
不是运行时依赖，不启动服务器。

可选联网冒烟测试只创建临时内存匿名会话，测试搜索、详情、歌词、取流及二维码申请/匿名轮询，
不读取本机真实账号，不执行扫码确认、收藏、打卡、播放上报、VIP 领奖等账号写操作：

```powershell
$env:NCM_NATIVE_LIVE_TEST = '1'
.\gradlew.bat :shared:jvmTest --tests '*NcmNativeLiveTest'
```

未设置该环境变量时正常测试不依赖网易云在线状态。联网测试通过也不等于所有会员音质、
扫码确认和账号记账都经过真实账号验证；这些需要明确的账号和平台运行时验证。
Windows 可以检查 iOS Kotlin 源码，不能替代 macOS/Xcode 链接与真机运行。

### 本次验证记录（2026-11-13）

- JVM：810 项，0 失败，3 项跳过；其中原生协议相关 96 项。
- Android host：569 项，0 失败；额外测试了 Android actual 的 BC provider，不能替代设备测试。
- ChromeHeadless/WASM：587 项，0 失败；原生密码/压缩入口在 Web 明确不可用，Web 仍走服务器。
- Android Debug 和四个 ABI 的 unsigned Release APK 构建成功，Release 经过 R8/资源压缩。
- Desktop Kotlin 编译、ProGuard release 合并与实际混淆 JAR 的密码探针通过：BC
  MD5/GCM/RSA/X25519、原生 provider 和 WEAPI。探针不启动 GUI，也不读取账号设置。
- Web 浏览器测试及 production distribution 成功。
- iOS arm64 与 simulator-arm64 的 Kotlin 编译成功；未在 Windows 上执行链接、Xcode 或真机测试。
- 独立 Node 向量生成器对固定上游实际 XEAPI/C/S/R、session、HMAC、公钥解密及 gzip 响应断言通过。
- 匿名只读联网测试通过：搜索、详情、歌词、取流、原有 `NCMApi` 门面、二维码申请及匿名轮询。
  测试期间曾有一次匿名注册被上游拒绝；再次独立测试通过，没有为此添加业务自动重试。
- 许可证资源与根声明一致，tracked-secret 检查通过。Release 构建仍有当前 R8/Kotlin metadata
  兼容告警，Web 构建仍有动态依赖与 bundle 体积告警；未通过隐藏告警来宣称构建无警告。

全量命令（Windows PowerShell）：

```powershell
.\gradlew.bat :shared:jvmTest :shared:testAndroidHostTest :desktopApp:compileKotlin `
  :androidApp:assembleDebug :androidApp:assembleRelease `
  :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 `
  :shared:wasmJsBrowserTest :shared:wasmJsBrowserDistribution --continue
.\gradlew.bat :desktopApp:proguardReleaseJars
```

上述结果不构成延迟、CPU、内存或功耗基准。省去服务器转发和第二运行时是架构变化；
性能差异仍需要在同一设备、网络、账号与缓存状态下测量。

## 更新维护

更新时固定新的上游 SHA，逐项复核被移植模块、`util/request.js`、`util/crypto.js`、
XEAPI 公钥注册和 `util/ncbl.js`；保留许可证声明，再生成对照向量、运行离线测试和匿名冒烟。
与服务器独立部署不同，协议修复需要随 App 发布，旧 App 不会自动得到服务器补丁。
