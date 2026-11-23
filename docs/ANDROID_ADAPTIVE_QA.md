# Android 折叠屏自适应验收

目标为 Google Play [Tier 3（Adaptive ready）](https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/tier-3)，不是 Tier 1 的姿态差异化或 Google Play 认证。依据 [2026 年 11 月公告](https://developer.android.com/blog/posts/google-play-adaptive-quality-standards)，折叠屏最低要求和三张折叠屏商店截图是 2027 年 4 月的独立提交要求；`targetSdk 37` 是公告中 2027 年 8 月的另一项要求。本次不升级构建工具链，也不操作 Play Console。

## 实现约束

- Launcher Activity 可自由旋转和调整大小，没有宽高比限制。窗口大小、屏幕布局、密度和外接键盘配置变化由现有 Activity/Compose 响应，不重启播放器或重建路由。系统语言、字体、主题等其余变化仍可重建 Activity，普通 UI 状态走 Android SavedState 和按路由隔离的 Compose 状态；账号草稿、QR/SMS 会话只保存在内存中，凭据/服务器地址只有用户显式提交才写入设置。
- 外屏、窄窗口、内屏的导航和内容根据实际约束排布，不用机型、屏幕物理尺寸或全局方向判断分屏大小。短窗口使用紧凑顶栏和可滚动内容；软键盘出现时收起底部浮层。播放页保持背景铺满，交互内容避开刘海、系统栏、桌面标题栏和手势区域。AndroidX WindowManager 报告分隔折痕时，Now Playing 和 AMLL 尽量把封面/歌词与控件放在两侧；空间不足则用一个可操作的安全区域。连续、无分隔的展开屏可利用全宽。
- ExoPlayer、队列、MediaSession 和下载管理不依赖 Activity 获焦。听歌识曲是例外：失去 top-resumed 所有权时中止采样并归还被它暂停的播放，焦点回来后由用户重试，不擅自重启录音。触摸屏和麦克风不作为安装硬件门槛。

## 2026-11-23 ADB 验证

| 官方用例 | 实测证据 | 尚需补验 |
|---|---|---|
| `T-Config_Orientation`、`T-Config_Combinations` | 小米 18 Fold（Android 17，440 dpi）用 `cmd device_state` 切换 OPENED / HALF_OPENED / CLOSED，系统默认窗口分别报告内屏 1672×2364、内屏横向 2364×1672、外屏 1168×1712，Activity 和进程未换。Android 16 模拟器用等比例分辨率分别覆盖 1:1、4:3、外屏 9:16 / 10:16，横竖转换后进入分屏、拖动分隔线、旋转、恢复全屏，再进入/调整自由窗口。其中 `areBoundsLetterboxed=false`，且 Activity/进程不变。 | 模拟器尺寸覆盖不等于官方折叠 AVD 的铰链。HyperOS 软件 CLOSED 时外屏在运行应用的测试中熄灭，即使发送 WAKEUP 未点亮，外屏真机画面、亮屏跨面板输入法与真实机械折叠仍需手动验收。 |
| `T-Config_State` | 真机搜索编辑器在竖屏 → 横屏 → 竖屏中保持草稿、焦点及开启的 IME；通过闭合→半开→展开时保留草稿。100 行合成搜索结果滚动后，回到展开竖屏仍是第 005 行，同样的可见偏移。模拟器同一草稿跨尺寸与方向保留；Back 先关闭 IME，不离开搜索。AMLL 的合成歌词、当前进度和控件在宽、方及短窗口可见。 | 真机闭合导致 OEM 关屏时 IME 被系统隐藏，不把该场景算作外屏 IME 通过。没有取得运行时强制重建 Activity 的独立 IME 样本；也未逐一验证所有登录页面和深分页的进程死亡恢复。 |
| `T-Multi-Window_Functionality`、`T-Multi-Window_Focus` | Android 16 模拟器与系统时钟进入真实分屏，拖动后测到 `608×304dp`、旋转后 `288×608dp`，两边仍可操作；约 `474×241dp` 的自由窗口可从首页进「我的 → 设置 → 账号」并滚到列表底部。实际播放 90 秒合成 WAV，时钟获焦且音乐应用仍可见时，MediaSession 保持 `PLAYING`，进度 `23,436 → 26,464ms`，退出分屏后页面为 `0:29`。 | HyperOS 的测试用系统设置页不能作为分屏搭档，真机未取得分屏实测；需使用非隐私且支持分屏的搭档另验。Googlebook/ChromeOS 的桌面窗口最小化、最大化与恢复未验；现有模拟器是 Android 自由窗口，不是完整桌面 Shell。 |
| `T-Multi-Window_Resources` | 自动化覆盖录音中切走即取消采样、归还播放、回到本窗口可手动重试，以及匹配结束时不得在后台重新抢占麦克风。 | 在真实分屏与另一录音应用之间交接麦克风。 |
| `T-Keyboard_Input`、`T-Mouse_Trackpad_Basic` | 模拟器直接注入键盘字符、Ctrl+A/Delete、Back，并实际点击软件键盘字符；鼠标点击导航、鼠标滚轮滚动账号列表。 | 实物键盘热插拔、触控板与文本双击选择的全页面覆盖。 |
| `T-Stylus_Basic`、`T-Stylus_Text_Input` | 文本输入走 Compose Foundation/Material 的原生编辑器，Android 14+ 的普通字段使用[系统手写支持](https://developer.android.com/develop/ui/compose/touch-input/stylus-input/stylus-input-in-text-fields)。密码/验证码沿用相应安全输入类型。 | 缺少兼容手写笔和 IME，实际手写编辑不视为通过。 |
| `T-Camera_Preview`、`T-Media_Projection` | 不适用：没有摄像预览或系统投屏录制；二维码是生成供另一设备扫码，不占用摄像头。 | — |

真机用第一版适配候选包验证折展和输入；短窗布局、模拟器窗口矩阵和分屏播放再用后续候选包复测。`.debug` 与正式应用并存，不覆盖正式应用。真机状态覆盖、模拟器分辨率/方向/输入法与自由窗口设置均已恢复；测试用内网映射与音频已移除。若真机调试版仍启用了测试用的 QQ 服务器，可在「账号与服务」关闭 QQ 音乐或恢复原地址，不影响正式版。测试截图使用合成歌词或无账号页面，不代表已提交的商店素材。

## 复测

```powershell
.\gradlew.bat :androidApp:assembleDebug :shared:jvmTest :shared:testAndroidHostTest :shared:compileKotlinWasmJs :desktopApp:compileKotlin
adb devices -l
adb -s <serial> install -r androidApp/build/outputs/apk/debug/androidApp-universal-debug.apk
adb -s <serial> shell am start -W -n com.leejlredstar.redefinencm.kmp.debug/com.leejlredstar.redefinencm.kmp.MainActivity
```

1. 用调试账号或合成数据打开搜索，输入未提交文本并显示软键盘。旋转、折叠/展开、进入分屏并拖动分隔线，比较草稿、光标/选择和焦点；先手动按 Back 关闭键盘再重复，键盘不应无故出现。对结果列表滚至中段后重复，比对可见条目与偏移。
2. 在正在播放、队列滚动后、歌词展开后重复上述变化。分屏中让时钟等不抢占音频焦点的应用获焦，用 `adb shell dumpsys media_session` 核对调试包的播放器状态与播放位置；再用录音应用复测麦克风交接。检查 `dumpsys activity activities` 和 `dumpsys window` 的 Activity、窗口矩形及 letterbox 状态，系统任务栏不算应用黑边。
3. 真机用实际铰链和 OEM 允许外屏继续使用应用的设置验证亮屏切换。再用 Android Studio 的 Foldable（1:1/4:3 inner display）官方 AVD 跑同样矩阵，连接外接键盘、鼠标/触控板和手写笔。按 [Google 的截图规格](https://developer.android.com/google/play/requirements/screenshots-android-cli) 准备 1:1 展开、4:3 展开、10:16 折叠三张真实屏幕素材；另保留标准的两张手机截图。
