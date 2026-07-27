# AMLL 原生 Compose 移植来源映射

本文记录 `NativeAmllScreen` 及其歌词引擎的代码来源、符号级映射和有意保留的平台 API 差异。它用于审计“哪段 TypeScript / CSS / 宿主 JavaScript 被翻译到哪个 Kotlin 符号”，不把尚未做跨平台截图比对的结果表述为像素完全等价。

## 固定的来源快照

### AMLL npm 包

移植时读取的是 npm 发布包中 source map 的 `sources` / `sourcesContent`，不是压缩后的 bundle：

| 包 | 版本 | 许可证 | source map | npm `dist.integrity` |
|---|---:|---|---|---|
| `@applemusic-like-lyrics/core` | `0.5.2` | `AGPL-3.0-only` | `dist/amll-core.mjs.map` | `sha512-RlFwDlOzT7gzHo/dbrG9noO1Q51bHLDrG1vIgtsf9x7+SNmRXww9pkNubLrDyrHSEiMDgtjEvoG824bYbTAVIQ==` |
| `@applemusic-like-lyrics/lyric` | `1.0.2` | `AGPL-3.0-only` | `dist/amll-lyric.mjs.map` | `sha512-dMCruzU1BERPkUFFcoumsT6oQL79nE9s3jmmhyVt9sXHG+87v1ZWFaC6j8SeyZzSur08i1xsivnCsly0ydrb5g==` |

上游仓库：<https://github.com/amll-dev/applemusic-like-lyrics>

### RedefineNCM AMLL 宿主

原生化之前的宿主基准固定在 Git 提交
`d3cfaf168d5f605d5bcea265055d84fedf691e6d`：

| 文件 | Git blob |
|---|---|
| `shared/src/commonMain/amllAssets/amll/player.html` | `1aa535041c73522f8b5e435ca1a6854ade91a253` |
| `shared/src/commonMain/amllAssets/amll/style.css` | `427b30db03b20fa596a8683bd3935e86c092948e` |
| `androidApp/amll-builder/entry.js` | `16a704321d594f6e1de5989431c4d98a15c2fe49` |

这些文件在原生实现中被删除；审计时可用
`git show d3cfaf168d5f605d5bcea265055d84fedf691e6d:<path>`
恢复对应内容。

## 解析、补充歌词与优化

| 来源文件 / 符号 | Kotlin 文件 / 符号 | 映射说明 |
|---|---|---|
| lyric `src/formats/lrc.ts`：`parseLrc` | `util/LyricParser.kt`：`parseLrcLines`、`parseLrcTime` | 逐个时间标签展开同一文本；保留重复时间行；识别整行中英文圆括号背景人声。 |
| lyric `src/utils.ts`：`createLine`、`createWord`、`parseTime` | `util/LyricParser.kt`：`WordLine`、`Word`、`parseLrcTime` | 将 JavaScript 数值时间转换为毫秒时间，保留后续优化所需的行级与字级字段。 |
| 旧 `entry.js`：`parseYrc`、`normalizeWordStart` | `util/LyricParser.kt`：`parseYrc`、`parseYrcLine`、`normalizeYrcWordStart` | 这里翻译的是 RedefineNCM 宿主实际使用的网易 YRC 方言，不以 lyric 包的通用 `src/formats/yrc.ts::parseYrc` 代替。该宿主方言不推断背景人声。 |
| 旧 `player.html`：`parseLrcSupplement`、`nearestSupplement`、`applyLyricOptions` | `ui/amll/AmllLyricModel.kt`：`parseSupplement`、`findNearestSupplement`、`buildAmllLyricDocument` | 翻译/罗马音按原宿主的 `850 ms` 最近时间容差挂接；显示开关仍由公共设置控制。 |
| core `src/utils/optimize-lyric.ts`：`optimizeLyricLines` | `ui/amll/AmllLyricModel.kt`：`optimizeAmllLyricLines` | 保持上游六个优化步骤的顺序和默认全部启用的行为。 |
| 同上：`normalizeSpaces` | 同名私有函数 `normalizeSpaces` | 连续 ECMAScript 空白归一为一个空格。 |
| 同上：`resetLineTimestamps` | 同名私有函数 `resetLineTimestamps` | 单词时间与行时间同步规则逐分支翻译。 |
| 同上：`convertExcessiveBackgroundLines` | 同名私有函数 `convertExcessiveBackgroundLines` | 连续第二条及之后的背景行转换为主行。 |
| 同上：`syncMainAndBackgroundLines` | 同名私有函数 `syncMainAndBackgroundLines` | 主行和紧随背景行共享最早开始、最晚结束。 |
| 同上：`cleanUnintentionalOverlaps` | 同名私有函数 `cleanUnintentionalOverlaps` | 保留 `100 ms` 与下一行时长 `10%` 的刻意重叠判定。 |
| 同上：`tryAdvanceStartTime` | 同名私有函数 `tryAdvanceStartTime` | 保留 `600 ms`、`400 ms`、上一行 `30%` 安全边界。 |
| core `src/lyric-player/dom/index.ts`：`setLyricLines` 中的分组循环 | `ui/amll/AmllLyricModel.kt`：`groupAmllLyricLines` | 每个非背景行开启一个组，随后的一条背景行附着到该组。 |

## 弹簧、时间线与布局引擎

| 来源文件 / 符号 | Kotlin 文件 / 符号 | 映射说明 |
|---|---|---|
| core `src/utils/derivative.ts`：`derivative`、`getVelocity` | `ui/amll/AmllSpring.kt`：`amllDerivative` | 中心差分步长保持 `0.001`。 |
| core `src/utils/spring.ts`：`Spring` | `ui/amll/AmllSpring.kt`：`AmllSpring` | 目标位置、延迟参数、延迟位置、速度与到达判定翻译为平台无关状态。 |
| 同上：`solveSpring` | `solveAmllSpringPosition` | 临界/过阻尼与欠阻尼两个解析分支及默认 `mass=1`、`damping=10`、`stiffness=100`、`soft=false` 保持一致。 |
| core `src/lyric-player/base/timeline.ts`：`computePlayerTimeState` | `ui/amll/AmllPlayerEngine.kt`：`computeAmllPlayerTimeState` | `hotGroups`、`bufferedGroups` 的新增和移除集合按相同边界条件计算。 |
| 同上：`pickScrollToIndexForSeek` | `pickAmllScrollToIndexForSeek` | seek 时的滚动目标选择。 |
| 同上：`commitPlayerTimeState` | `commitAmllPlayerTimeState` | seek / 连续播放下启用、禁用、缓冲和末尾行行为。 |
| core `src/lyric-player/base/layout.ts`：`computeCurrentInterlude` | `computeAmllCurrentInterlude` | 保留 `+20 ms` 偏移、下一句前 `250 ms`、最短 `4000 ms` 的间奏条件。 |
| 同上：`computeLinePosYSpringParams` | `computeAmllLinePosYSpringParameters` | 行间隔钳制、幂函数刚度插值和 `2.2` 阻尼倍数。 |
| 同上：`computeGroupPresentation` | `computeAmllGroupPresentation` | 活跃状态、已过行隐藏条件、透明度、模糊和渲染模式。 |
| 同上：`computeLineBlur` | `computeAmllLineBlur` | 用户滚动、活跃行和前后行的模糊分支。 |
| core `src/lyric-player/base/index.ts`：`setCurrentTime` | `AmllPlayerEngine.setTime` | 热区计算与提交集中到一次时间更新。 |
| 同上：`onPageHide`、`onPageShow`、`isPageVisible` | `AmllPlatformEvents.wasm.kt` 的 `pagehide` / `pageshow` / `visibilitychange` bridge；`AmllLyricViewport` 的 `isPageVisible` 与恢复同步 | 隐藏时仍允许公共时间状态接收播放器更新，但停止歌词组弹簧；恢复时逐行对应 `setCurrentTime(currentTime, true)` 重新同步。Web 播放器自身的既有页面离开监听同时执行 `pause()`，从而取消尚未完成的流地址解析。 |
| 同上：`calcLayout` | `AmllPlayerEngine.layout`、`calculateAmllLayoutTargets`、`calculateAmllGroupTarget` | 对齐锚点、间奏占位、滚动偏移、延迟、底部行和每组目标统一计算。 |
| 同上：`pause`、`resume`、`update` | `AmllPlayerEngine.setPlaying`、`update` | Compose 帧循环提供上游 `update(delta)` 的时间增量。 |
| core `src/lyric-player/base/group.ts`：`setTransform`、`setLineTransformations`、`update` | `computeAmllGroupPresentation`、`AmllGroupTarget`、`AmllGroupFrame` | 主/背景行缩放、背景 `±80%` 滑入和延迟弹簧状态。 |
| core `src/lyric-player/dom/lyric-group.ts`：`addBgLine`、`renderStyles` | `AmllLyricGroupContent`、`AmllGroupFrame.backgroundSlideYPercent`、`backgroundWrapperScale` | 背景行前置/后置、活动时进入流式布局、隐藏时缩放到 `0.8` 的结构映射。 |
| core `src/lyric-player/base/scroll.ts`：`clampPlayerScrollOffset`、`resetPlayerScrollState`、`attachPlayerScrollHandlers` | `ui/amll/AmllLyricViewport.kt`：`AmllViewportRuntime.clampScroll`、`resetScroll` 及 pointer / wheel 处理；`wasmJsMain/ui/amll/AmllPlatformEvents.wasm.kt`：原生 DOM wheel 捕获 | 保留点击移动阈值 `10 px`、惯性停止阈值和 `0.95 ** (dt / 16)` 摩擦公式；Web 在 CanvasKit 前读取真实 `WheelEvent.deltaMode`，PIXEL 直加并同步布局，LINE/PAGE 乘 `50` 并异步布局；5 秒自动复位由视口帧循环执行。 |

## 字级渲染、换行与间奏

| 来源文件 / 符号 | Kotlin 文件 / 符号 | 映射说明 |
|---|---|---|
| core `src/lyric-player/dom/lyric-line.ts`：`generateFadeGradient`、`generateCalcBasedMaskImage`、`generateWebAnimationBasedMaskImage` | `ui/amll/AmllWordRenderer.kt`：`computeAmllMaskFrame`、`buildAmllWebMaskTimeline`、`sampleAmllWebMaskTimeline`、`withAmllMaskLayer` | CSS `mask-image` / Web Animation 的位置和渐变计算转为 Compose `saveLayer`；文字、动态 shadow 与宿主 shadow 合成后统一以 `DstIn` 遮罩。ruby 路径按每个非空子段及其 UTF-16 unit 推进，并保留子段间静止帧、词边界夹取和无有效 ruby 时的逐词回退。 |
| 同上：`ANIMATION_FRAME_QUANTITY`、`makeEmpEasing`、`initFloatAnimation`、`disable` | `AmllAnimationFrameQuantity`、`computeAmllEmphasisFrame`、`computeAmllNormalFloatProgress`、`drawEmphasizedAtom` | 保留 32 帧采样、两段贝塞尔缓动、逐字符错峰、缩放/位移/发光与浮动公式；停用时只把上游实际命中的 `float-word` 按当前时间反向到零。 |
| 同上：`updateMaskAlphaTargets`、`applyAlphaToDom` | `targetAmllMaskAlpha`、`advanceAmllMaskAlpha` 与 `AmllTimedWordLine` | `SOLID` / `GRADIENT` 目标 alpha、`50` attack、`7` release、`1-exp(-speed*dt)`、`0.001` snap 和三位小数可见值按上游分支实现；禁用弹簧时直接写目标值。 |
| core `src/utils/lyric-split-words.ts`：`chunkAndSplitLyricWords` | `AmllWordRenderer.kt`：`chunkAndSplitAmllLyricWords`、`buildAmllRenderAtoms`；`AmllTextSegmentation.kt` 与各平台 actual | 空白、CJK 与连续非 CJK 单词的原子化结果供测量与逐字动画使用；`.length` 与 CJK `split("")` 分别由 UTF-16 unit 计数和逐 Kotlin `Char` 切分保留，不能替换成 code point / grapheme。带 ruby 的词保持结构化原子；非空 `romanWord` 阻止 CJK 单字拆分；`LyricParser.Word` 将 ruby、逐词 roman、obscene 和浮点时间完整传到渲染层。 |
| core `src/utils/lyric-line-break.ts`：`calcBalancedBreaks` | `calculateAmllBalancedBreaks` | 动态规划、溢出惩罚、CJK/普通边界惩罚、空格/标点奖励常量按上游值翻译。 |
| core `src/utils/line-balancer.ts`：`LineBalancer`；`dom/lyric-line.ts` constructor / `rebuildElement` | `AmllTimedWordLine`、`amllLineBreakStrategy`、`calculateAmllBalancedBreaks`、`calculateAmllNativeFlowBreaks` | 有平台 word segmenter 时才执行平衡算法。不可用时不合成词段：静态行保留完整文本交给 `BasicText` 普通换行，动态 wrapper 仅按源顺序贪心流式换行，不运行动态规划。 |
| core `src/lyric-player/dom/interlude-dots.ts`：`InterludeDots.update`、`easeInOutBack`、`easeOutExpo` | `ui/amll/AmllLyricViewport.kt`：`computeAmllInterludeVisual`、`easeInOutBack`、`easeOutExpo`、`AmllInterludeDotsVisual` | 呼吸、三点渐入、开头/结尾缩放和透明度时间窗按上游公式计算。 |

## CSS selector 到 Compose 结构

来源均为 core `src/styles/lyric-player.module.css`，同时以旧
`shared/src/commonMain/amllAssets/amll/style.css` 的实际打包结果复核。

| CSS selector / variable | Compose 位置 | 说明 |
|---|---|---|
| `.amll-lyric-player` | `NativeAmllScreen`、`AmllLyricViewport`、`calculateAmllLyricVisualParameters` | 根视口、字体尺寸、裁剪和播放状态。 |
| `--lyric-line-padding-x` | `AmllLyricGroupContent` | 默认 `1em`、窄视口 `20px`；Android 宿主覆盖按平台分支处理。 |
| `.FmKaba_lyricLineWrapper`、`:hover`、`:active` | `AmllLyricGroupContent` | `.4em` 垂直 padding、`.25em` 圆角、`.3em` 间距、hover/press 背景。 |
| `.FmKaba_lyricLine`、`.FmKaba_lyricDuetLine` | `AmllLyricLineContent` | 主/对唱对齐、变换原点、行内 padding。 |
| `.FmKaba_lyricMainLine` | `AmllStaticMainText`、`AmllTimedWordLine` | 静态行或字级 Canvas 行。 |
| `.FmKaba_emphasize > span` 与逐字符 Web Animation | `AmllAtomTextLayout.glyphs`、`drawEmphasizedAtom` | 每个 grapheme 独立测量、独立 transform 与动态白色 shadow，不再用整词排版裁切字符。带 ruby 的结构化词创建等价 `wordBody` 层，主词体和逐词 roman 直接复现旧宿主的固定紫色 glow、描边、drop-shadow 和前景 alpha；强调错峰锚点使用未过滤 ruby 字符总数。 |
| `.FmKaba_lyricBgLine` | `AmllLyricGroupContent` | `.7em`（最小 `10px`）、`0.4` alpha 和背景行布局。 |
| `.FmKaba_lyricSubLine` | `AmllSubLine` | `.5em`（最小 `10px`）、`0.3` alpha、`1.5em` 行高。 |
| `.FmKaba_bgWrapper*` | `AmllLyricGroupContent`、`AmllGroupFrame` | 背景行的前/后位置、显隐、滑动和活动时占位。 |
| `.FmKaba_interludeDots*` | `AmllInterludeDotsVisual` | 点尺寸、间距、对唱对齐和时间动画。 |
| `.FmKaba_disableSpring`、`.FmKaba_tmpDisableTransition` | `AmllPlayerEngine` 的 `enableSpring` / `force`、`rememberReducedMotionEnabled` | 禁用弹簧时使用同步目标或有限过渡。 |
| `.FmKaba_hasDuetLine ...` | `AmllLyricLineContent`、`AmllLyricGroupContent` | 对唱侧 15% 留白、尾端对齐和背景变换原点。 |

`--amll-lp-line-width-aspect: .8` 虽存在于 CSS，但 core
`dom/lyric-line.ts::rebuildStyle` 中写入 `--amll-lp-width` 的代码被注释；实际 DOM
默认宽度来自 `var(--amll-lp-width, 100%)`。因此原生布局以实际生效的 `100%`
为基准，不把未消费的 `.8` 当成渲染宽度。

## RedefineNCM 宿主页面到公共 Compose

| 旧宿主来源 | Kotlin 位置 | 映射说明 |
|---|---|---|
| `player.html`：`#bg`、`#dynamic-bg`、`#bg-scrim` | `ui/amll/AmllBackground.kt`、`ui/component/NativeDynamicCoverLayer.kt` | 封面裁剪、桌面/Android 两组 blur / brightness / saturation / scale、400 ms 换图和横向黑色遮罩。 |
| `player.html`：`#desktop-back`、`#wiki-info` | `NativeAmllScreen.kt`：`AmllTopActions`；`SongWikiDetailsButton` | 返回和歌曲详情入口直接进入同一公共 Compose 树。 |
| `player.html`：`#wiki-overlay`、`#wiki-dialog`、`.wiki-*` selector 与 `renderSongWiki` | `ui/component/SongWikiDetails.kt`：`SongWikiDetailsSheet`、`WikiDialogContent`、`WikiHero`、`WikiMetadata`、`WikiStatePanel`、`WikiSectionCard` | 颜色 token、桌面/移动/低高度断点、加载/空/错误/内容状态、焦点与关闭交互；`.wiki-hero` 的 `linear-gradient(135deg, …)` 使用 CSS magic-corner 投影轴，而非按盒子对角线近似。 |
| `player.html`：`setSongDetails`、`setSongWiki*`、media-id guard | `NativeAmllScreen` 的 `SongWikiUiState.scopedTo` 及 `NowPlayingViewModel` 的歌曲详情状态 | 不再经过 JavaScript bridge；仍以当前媒体 ID 丢弃过期结果，切歌关闭详情。 |
| `player.html`：`installReducedMotionPreference`、`prefersReducedMotion` | `ui/amll/ReducedMotion.kt` 及各平台 actual | 操作系统减少动态效果设置进入歌词弹簧、背景换图、详情动画与动态封面决策。Android、iOS 和 Web 监听运行时变化；Windows/JVM 读取系统设置，macOS/Linux 当前无等价 JVM API，边界见差异清单。 |
| `player.html`：`line-click`、键盘标签与 live region | `AmllLyricViewport` 的点击/键盘输入和 Compose semantics | 每组歌词持有稳定 `FocusRequester` 和 roving `focusProperties`；活动/回退组进入 Tab 序列，方向键、Home/End、Enter/Space 与点击后的焦点转移按旧 DOM 行为执行。直接调用 `onSeek(mediaId, timeMs)`，不再解析 DOM data attribute 或调用宿主 bridge。 |
| `entry.js`：`LyricPlayer` 初始化、`setCurrentTime`、`setPlaying` | `NativeAmllScreen`、`AmllPlayerEngine` | Compose 生命周期和 `withFrameNanos` 替代脚本加载、`requestAnimationFrame` 与 bridge 初始化。 |

## 有意的平台 / API 差异

以下差异来自浏览器 DOM 与 Compose Multiplatform API 边界，不应被描述成字节级或像素级同构：

1. **渲染后端和字体度量。** DOM/CSS 文本布局改为 Compose
   `TextMeasurer`、Canvas 和平台字体栅格器。相同字号、时间和布局公式不保证不同平台的
   glyph hinting、fallback 字体、字距与像素舍入完全一致。
2. **分词 API。** Web/WASM 直接调用与上游相同的
   `Intl.Segmenter(undefined, { granularity: "word" | "grapheme" })`；Android 使用 ICU
   `BreakIterator`，JVM 使用系统 word `BreakIterator` 与 `\X`，iOS 使用 CoreFoundation
   word tokenizer / composed-character range。平台 API 不可用或结果不保序时进入显式
   `FALLBACK`。word `FALLBACK` 不生成任何伪词段：和上游未构造 LineBalancer 的分支
   一样，静态歌词保留完整文本，动态歌词保留既有 wrapper 原子并使用普通顺序流式换行；
   Compose 与浏览器的普通断行器仍可能选择不同的 Unicode 换行机会。grapheme
   `FALLBACK` 按上游 `Array.from` 的 code point 语义。后三个平台的词典、Unicode
   版本和 locale 数据也不保证与 Chromium 的 `Intl.Segmenter` 完全相同。
3. **CSS 合成提示。** `contain`、`content-visibility`、`will-change`、
   `backface-visibility` 没有一一对应的 Compose 属性；它们是浏览器优化提示，不参与
   Kotlin 引擎的时间线和目标值计算。
4. **混合与滤镜实现。** CSS `mix-blend-mode: plus-lighter`、`mask-image`、
   `filter` 和 `backdrop-filter` 由 Compose 图层、Canvas mask、blur、ColorMatrix
   和遮罩层实现。GPU 后端和颜色空间不同会产生小的边缘/亮度差异。
5. **尺寸单位。** CSS `px` / `vw` / `vh` / media query 映射到 Compose 约束和
   `dp` / `sp`。断点语义保持一致，但操作系统缩放后不保证 CSS 像素和 dp 物理尺寸相同。
6. **输入与无障碍。** DOM Pointer/Touch/Keyboard 事件和 ARIA 被替换为
   Compose pointer / key input 与 semantics；Web 的 wheel 由 DOM capture bridge 保留原始
   `deltaMode` 后送入同一个公共滚动状态。每个歌词组是真实焦点节点；键盘/Tab 焦点绘制
   旧 CSS 的 `3px rgba(255,255,255,.92)`、`2px` offset、`12px` 圆角外环，pointer
   点击聚焦但不伪装 `:focus-visible`。CanvasKit 把全部
   Compose 歌词行绘制进同一个 `<canvas>`，无障碍 DOM 镜像又使用
   `pointer-events: none`；因此 `document.elementFromPoint()` 只能命中 canvas，不能返回
   上游的 `.lyricLineWrapper`。触摸释放命中继续由公共布局中按逆绘制顺序的歌词组边界
   计算，未把 canvas 命中伪装成逐行 DOM 命中。
7. **动态视频是窄平台叶。** `NativeDynamicCoverLayer` 不决定页面结构。Android
   通过 Media3 / `TextureView` 显示视频；iOS 通过 `AVPlayerLayer` / `UIKitView`；
   Desktop/JVM 通过 FFmpeg 解码并把帧交给 Compose `ImageBitmap`；Web/WASM 通过
   `HTMLVideoElement` 与 CanvasKit 透明区域互操作。视频解码和原生互操作按平台实现，
   其余背景滤镜、遮罩和页面结构仍由公共 Compose 树控制。视频只有在公共
   `requestedPlay` 与平台生命周期同时为真时播放：Android 要求宿主至少 `STARTED`，
   iOS 跟随 UIApplication 前后台，Desktop 要求窗口可见且非最小化，Web 跟随
   `visibilitychange` / `pagehide` / `pageshow`；恢复生命周期不会绕过公共播放请求。
8. **宿主通信被移除。** `@JavascriptInterface`、WebView2 bind、DOM observer 和
   `AmllPage` 全局对象不再存在；公共 state flow、Compose 状态和直接 Kotlin 回调承担
   同一数据流。
9. **响应系统设置。** 减少动态效果、hover、forced colors / high contrast 等能力由
   各平台 Compose 与 actual API 提供。Android、iOS、Web 可在运行时通知减少动态效果；
   Windows/JVM 当前在创建组合时读取系统值但不订阅后续注册表变化，macOS/Linux JVM
   返回默认关闭。平台没有等价 API 时采用静态或默认样式。

## 许可证

上述由 AMLL 翻译或改编的 Kotlin 文件使用
`SPDX-License-Identifier: AGPL-3.0-only` 标记。版权与修改说明见
[`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md)，完整许可证文本见
[`THIRD_PARTY_LICENSES/AGPL-3.0-only.txt`](../THIRD_PARTY_LICENSES/AGPL-3.0-only.txt)。
