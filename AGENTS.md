# AGENTS.md — RedefineNCM KMP (Kotlin Multiplatform)

Guidance for AI coding agents working in this repository. **This document is the
authoritative target specification.** It describes the architecture the project is
converging toward, the decisions that have been locked in, and — in one bounded section
near the end (["Current divergence from this spec"](#current-divergence-from-this-spec)) —
the ways the code on disk does not yet match it.

> **Read this first, then read [`../RedefineNCM/AGENTS.md`](../RedefineNCM/AGENTS.md).**
> The original Android app is the reference implementation for feature behavior.

> ⚠️ **This document distinguishes _decision_ from _fact_.** Architecture decisions (which
> targets, which source-set names, which library replaces which) are made here and are
> binding. External facts that this environment cannot verify — chiefly "what is the latest
> published version of library X" — are **never asserted**. Where a version must be chosen,
> the doc says so and defers to a verification pass. The previous version of this file
> asserted aspiration as fact (claimed dependencies, versions, and SQLDelight that do not
> exist); that is the failure mode this rewrite exists to prevent. Do not reintroduce it.

---

## What this is

RedefineNCM KMP is a Kotlin Multiplatform port of the original Android-only
[RedefineNCM](https://github.com/RedSTARO/RedefineNCM) — a third-party **NetEase Cloud Music
(网易云音乐)** client. The original Android codebase is in the sibling `../RedefineNCM`
directory and is the reference for feature behavior, API encoding, and UI/interaction design.

Targets, in priority order:

| Target | Priority | Distinguishing platform feature |
|---|---|---|
| **Android** | P0 | Live-update **notification** lyric (`MediaStyle` / custom notification) |
| **iOS** | P0 | **Live Activities → 灵动岛 (Dynamic Island)** + Lock Screen lyric |
| **Desktop / JVM** | P0 | **Floating always-on-top desktop-lyrics window** + **Windows SMTC** (System Media Transport Controls); MPRIS on Linux, MPNowPlayingInfoCenter on macOS |
| **Web / WASM** | P3 (lowest) | Basic playback; scaffolded, not feature-complete |

The app talks to a self-hosted
[NeteaseCloudMusicApi](https://github.com/Binaryify/NeteaseCloudMusicApi)-style backend
(base URL is user-configurable in Settings). All API communication is JSON over HTTP,
typically against a server on the same LAN or localhost (so cleartext HTTP must be allowed).

### Active goals driving current work

1. Complete the KMP migration to all four targets (Android, iOS, Desktop first; Web last).
2. Per-platform "now playing" surfaces: Android notification → **iOS Dynamic Island**,
   **desktop floating lyric window**, **Windows media protocol (SMTC)**.
3. **Material 3 Expressive** UI, applied consistently here **and** kept aligned in the
   original Android repo.
4. **Upgrade all dependencies to latest** — see [Dependency management](#dependency-management).
   This is a *verified* pass (check Maven Central / release notes); it must also **converge
   the two repos' shared versions** (Kotlin, coroutines, serialization, AGP), which are
   currently skewed.

---

## Locked architecture decisions

These were ambiguous or contradicted by the prior doc. They are now decided. Do not
re-litigate without recording a new decision here.

### D1 — Desktop source set is `jvmMain` (not `desktopMain`)

`shared/build.gradle.kts` declares the desktop target as a **bare `jvm()`**, so the Kotlin
Gradle plugin names the source set **`jvmMain`** (and `jvmTest`). The `desktopMain/` and
`desktopTest/` directories present on disk are **dead, byte-identical duplicates** and must
be deleted. All desktop-specific code lives in `jvmMain/`. (If a future change switches to
`jvm("desktop")`, do the rename in one commit and update this decision — do not leave both.)

### D2 — `wasmJs` target is intentionally deferred (web is P3)

`wasmJsMain/` source exists but the `wasmJs` target is **deliberately not declared** in
`shared/build.gradle.kts` yet — so that source is currently dormant (not compiled). Rationale:
web is the lowest priority, and declaring the target pulls web-only deps whose versions add
risk to the P0 build (`ktor-client-js`, `kotlinx-browser` for `localStorage`). When web is
prioritized, declare `wasmJs { browser(); binaries.executable() }`, add those deps, and
reconcile `Platform.wasm.kt` with the real `Platform` interface. Stubs are acceptable; full
parity is not required.

### D3 — Local cache is SQLDelight, and it does **not exist yet**

The original caches via Room (cache-then-network). The KMP replacement is **SQLDelight**.
As of this writing there is **no SQLDelight plugin, no `.sq` files, and no driver wiring** in
this repo — it is a TODO, not a current capability. Do not describe it as present until the
plugin + schema + drivers are actually added. Until then `Repository` is network-only.

### D4 — Notification / now-playing surfaces are one common contract, four platform actuals

`commonMain` owns the contract; each platform renders it natively:

- **`notification/LyricNotificationController`** — `expect object` with
  `updateLyric(title, artist, currentLyric, nextLyric, artworkUri)`, `clearFocus()`,
  `reset()`. This is the **lyric display** surface.
- **`smtc/MediaControlsIntegrator`** — common `object` holding `MediaControlMetadata`
  (title/artist/album/artwork/duration/position/isPlaying) for OS media controls. This is
  the **transport controls** surface. (It currently lives in a file misnamed
  `WindowsSmtcIntegration.kt` — see divergence list.)

Platform actuals:

| Platform | Lyric surface (`LyricNotificationController`) | Transport surface |
|---|---|---|
| Android | Live-update `Notification` (MediaStyle / custom RemoteViews) on a channel | Media3 `MediaSession` |
| iOS | ActivityKit **Live Activity** → Dynamic Island + Lock Screen | `MPNowPlayingInfoCenter` / `MPRemoteCommandCenter` |
| Desktop (JVM) | Frameless, always-on-top **Compose floating window** | **Windows SMTC** (JNA/COM); **MPRIS** (D-Bus) on Linux |
| Web (WASM) | No-op stub | No-op stub |

**iOS bridge pattern (decided):** Kotlin does **not** call ActivityKit directly. The Kotlin
`LyricNotificationController` writes a `LiveActivityData` value (mirrored as a Swift
`Codable`) into an **App Group `NSUserDefaults`**; a **Swift Widget Extension** target reads
it and drives the Live Activity. The Widget Extension target does not exist in the Xcode
project yet (TODO).

---

## Toolchain

Versions below are the **actual current state** of `gradle/libs.versions.toml` and
`gradle.properties` — not a recommendation and not necessarily "latest" (goal #4 is a
separate, verified upgrade pass; see [Dependency management](#dependency-management)).

| Component | Current value | Source of truth |
|---|---|---|
| AGP | `9.0.1` | `libs.versions.toml: agp` — see note: 9.2.0 deferred |
| Kotlin | `2.4.0` | `libs.versions.toml: kotlin` — bumped + build-verified 2026-06-11 |
| Compose Multiplatform | `1.11.1` | `libs.versions.toml: composeMultiplatform` |
| Compose Material3 (JB) | `1.11.0-alpha07` | `libs.versions.toml: material3` (pinned independently; Expressive APIs) |
| kotlinx-coroutines | `1.11.0` | `libs.versions.toml: kotlinx-coroutines` |
| Android compileSdk / minSdk / targetSdk | `36` / `24` / `36` | `libs.versions.toml: android-*` |
| Gradle (wrapper) | `9.1.0` | `gradle/wrapper/gradle-wrapper.properties` |
| JDK | 17+ | — |

> **Convergence status (goal #4):** Kotlin is now `2.4.0` in BOTH repos (converged + verified).
> **AGP stays at `9.0.1` here** while the original is `9.2.0`: bumping the KMP repo to AGP 9.2.0
> requires Gradle ≥ 9.4.1, and after that it fails to resolve `aapt2:9.2.0` and breaks
> configuration-cache serialization (empirically tested 2026-06-11). Not worth breaking the green
> build for AGP parity — revisit when those AGP-9.2 issues are resolved or config-cache is off.

> **Build IS verifiable here (Android + Desktop), corrected 2026-06-11.** The Android SDK is
> present and the JVM toolchain works. **Verified green:** `:shared:compileKotlinJvm`,
> `:desktopApp:compileKotlin`, `:shared:jvmTest` (PlayQueueTest passes), `:shared:compileAndroidMain`,
> `:androidApp:compileDebugKotlin`, full `:androidApp:assembleDebug` (APK). **Only iOS can't build
> here** (Windows, no Xcode — `iosSimulatorArm64Test` is auto-disabled). **After editing, run the
> relevant compile/test task** — reasoning alone missed real errors the build caught (a `/*` in a
> KDoc, `parameter()` not on `defaultRequest`, missing `ImageColorExtractor` actuals, etc.). Do NOT
> run GUI tasks (`:desktopApp:run`/`hotRun`) non-interactively — they hang.

### Version-coupling rules (when editing versions)

- Kotlin, Compose Multiplatform, and the Compose Compiler plugin are tightly coupled. The
  Compose Compiler plugin (`org.jetbrains.kotlin.plugin.compose`) version **is** the `kotlin`
  version ref. Compose Multiplatform and the JB `material3` artifact are pinned separately.
- AGP ↔ Kotlin compatibility: check Google's
  [AGP/Kotlin support table](https://developer.android.com/build/kotlin-support).
- CMP ↔ Kotlin compatibility: check
  [Compose Multiplatform releases](https://github.com/JetBrains/compose-multiplatform/releases).
- KSP is **not** used. Do not add it unless a specific annotation processor requires it.
- If SQLDelight is added (D3), its Gradle plugin version and runtime version MUST match.

---

## Module & source-set layout

```
RedefineNCM_KMP/
├── shared/                              # KMP shared module — business logic + shared Compose UI
│   ├── build.gradle.kts                 # KMP plugin config, source sets, dependencies
│   └── src/
│       ├── commonMain/kotlin/com/leejlredstar/redefinencm/kmp/
│       │   ├── App.kt                   # Root composable (⚠ still template placeholder)
│       │   ├── Platform.kt              # expect fun getPlatform(): Platform
│       │   ├── Greeting.kt / GreetingUtil.kt   # ⚠ template leftovers — delete
│       │   ├── data/
│       │   │   ├── Repository.kt        # Wraps NCMApi + safeApiCall, exposes Flows (network-only)
│       │   │   └── api/
│       │   │       ├── HttpClientFactory.kt   # Ktor client factory + safeApiCall<T>()
│       │   │       ├── NCMApi.kt              # NeteaseCloudMusicApi client (Ktor)
│       │   │       └── dto/Models.kt          # @Serializable DTOs
│       │   ├── di/Modules.kt            # Koin sharedModule + expect fun platformModule()
│       │   ├── player/
│       │   │   ├── PlatformPlayer.kt    # interface + PlayerState enum + MediaInfo + StreamUrlResolver
│       │   │   └── LyricBus.kt          # Shared lyric/position event bus (MutableStateFlow)
│       │   ├── notification/
│       │   │   └── LyricNotificationController.kt   # expect object (lyric display surface)
│       │   ├── smtc/
│       │   │   └── (MediaControlsIntegrator)        # ⚠ currently in jvmMain, not commonMain — see divergence
│       │   ├── util/
│       │   │   ├── LyricParser.kt        # Pure-Kotlin LRC parser
│       │   │   ├── ImageColorExtractor.kt # expect class — album-art accent extraction
│       │   │   └── Settings.kt           # expect class PlatformSettings + SettingKeys
│       │   ├── viewmodel/
│       │   │   ├── LoginViewModel.kt
│       │   │   ├── MainViewModel.kt
│       │   │   └── NowPlayingViewModel.kt # holds shuffle invariant (rebuildPlaylistFromTimeline)
│       │   └── ui/
│       │       ├── screen/NowPlayingScreen.kt   # ⚠ exists but not wired into App()
│       │       ├── component/Expressive.kt      # connected-list shapes
│       │       └── theme/{Color,Type,Theme}.kt  # M3 Expressive shapes/type (approximation — see #3)
│       ├── androidMain/  …/Platform.android.kt, notification/AndroidNotificationController.kt,
│       │                  di/AndroidPlatformModule.kt, util/AndroidSettings.kt
│       ├── iosMain/      …/Platform.ios.kt, MainViewController.kt,
│       │                  notification/IosLiveActivityController.kt (Live Activity data bridge),
│       │                  di/IosPlatformModule.kt, util/IosSettings.kt
│       ├── jvmMain/      …/Platform.jvm.kt, notification/DesktopFloatingWindowController.kt,
│       │                  smtc/WindowsSmtcIntegration.kt (⚠ data-model only; misnamed),
│       │                  di/JvmPlatformModule.kt, util/JvmSettings.kt
│       ├── desktopMain/  ⚠ DEAD byte-identical duplicate of jvmMain — DELETE (decision D1)
│       ├── wasmJsMain/   …/Platform.wasm.kt, notification/WasmNotificationStub.kt,
│       │                  di/WasmPlatformModule.kt, util/WasmSettings.kt  (⚠ target not declared — D2)
│       ├── commonTest/   SharedCommonTest.kt (placeholder; PlayQueueTest TODO)
│       ├── androidHostTest/ · iosTest/ · jvmTest/   (placeholder tests)
│       └── desktopTest/  ⚠ DEAD duplicate of jvmTest — DELETE
├── androidApp/    AGP application; MainActivity; depends on :shared
├── desktopApp/    Compose Desktop app; main.kt; depends on :shared
├── iosApp/        Xcode project (SwiftUI shell + ComposeUIViewController). NOT a Gradle module.
├── gradle/libs.versions.toml   # single source of truth for ALL versions
├── build.gradle.kts            # root — applies plugins with `apply false`
├── settings.gradle.kts         # includes :shared, :androidApp, :desktopApp
└── gradle.properties
```

### Key structural points

- **`:shared` is an Android *library*** via `com.android.kotlin.multiplatform.library`
  (it produces an AAR for the Android target). Android-only deps (Media3, Palette, DataStore)
  are scoped to `androidMain`.
- **`iosApp` is not a Gradle module** — it's an Xcode project consuming the `Shared`
  framework. Do not add `include(":iosApp")` to `settings.gradle.kts`.
- The package is `com.leejlredstar.redefinencm.kmp` (the original Android app used
  `com.redstar.redefinencm`). All new KMP code goes under the new package.

---

## How playback & lyrics work (ported contract)

1. A queue is a `List<MediaInfo>` whose `placeholderUri` is
   `redefinencm://playbackPlaceHolder?id=<songId>` — **not** a real audio URL.
2. Each platform's `PlatformPlayer` resolves the placeholder to a real CDN stream URL at play
   time via `NCMApi.songUrlV1()` (a `StreamUrlResolver`). Stream URLs are **never persisted**
   — they expire and are fetched fresh per session.
3. `NowPlayingViewModel` mirrors `PlatformPlayer`'s `StateFlow`s (state, position, duration,
   currentMedia, queue, currentIndex, shuffleEnabled) to the UI.
4. Lyric pipeline: `PlatformPlayer.position` → `LyricBus` → `LyricParser` matches the parsed
   LRC `LinkedHashMap<Long?, String?>` → current index → UI scroll **and**
   `LyricNotificationController.updateLyric(...)` (notification / Dynamic Island / floating
   window).

### Shuffle / queue ordering invariant (FROM ORIGINAL — DO NOT BREAK)

The visible queue (`playList`), the ordering it implies, and the current-item highlight
(`currentMediaIndexInList`) **must always be rebuilt together** from the player's current
state, via a single `rebuildPlaylistFromTimeline()` path. Every track transition, shuffle
toggle, and timeline change goes through that one function. Never read one value from cache
and another from live state — that is the original shuffle-misalignment bug.

The pure, unit-tested model of this now lives in **`player/PlayQueue.kt`** (immutable;
`currentItem == itemsInPlayOrder[positionInPlayOrder]` holds by construction) with a full
regression suite in **`commonTest/.../player/PlayQueueTest.kt`**. `PlatformPlayer` actuals
should delegate ordering to `PlayQueue` rather than re-deriving it. `NowPlayingViewModel`
still uses the simplified `player.queue` + `player.currentIndex` form and should be migrated
to drive `PlayQueue` when the real players land.

---

## Dependency management

`gradle/libs.versions.toml` is the **single source of truth**. Never hardcode versions in
`build.gradle.kts`; reference via `libs.<alias>`.

### Declared dependency set (matches what the code imports)

As of the 2026-06-11 build-fix pass, `libs.versions.toml` + `shared/build.gradle.kts` declare
exactly what the source imports — verified by grepping every third-party `import` in
`shared/src`. Versions are the verified June-2026 latest **compatible with the existing
Kotlin 2.3.21 pairing** (the toolchain bump to 2.4.0/AGP 9.2.0 was deliberately NOT bundled
in — see Goal #4).

| Area | Library (version) | Where |
|---|---|---|
| HTTP | Ktor `3.5.0`: client-core, content-negotiation, logging, serialization-kotlinx-json | commonMain |
| HTTP engines | okhttp (androidMain), darwin (iosMain), cio (jvmMain) | per-platform |
| Serialization | `kotlinx-serialization-json 1.11.0` + `kotlin.plugin.serialization` (@kotlin) | commonMain / plugins |
| Concurrency | `kotlinx-coroutines-core 1.11.0` | commonMain |
| DI | Koin `4.2.1`: koin-core, koin-compose | commonMain |
| Images | Coil `3.5.0`: coil-compose (group `io.coil-kt.coil3`) | commonMain |
| Android settings | `androidx.datastore:datastore-preferences 1.2.0` | androidMain |

**Deliberately NOT dependencies** (grep-confirmed the code does not import them; the *previous*
AGENTS.md wrongly listed them):
- **`multiplatform-settings`** — `PlatformSettings` is a hand-rolled `expect class`; actuals use
  DataStore (Android), `NSUserDefaults` (iOS), JVM prefs, `kotlinx.browser.localStorage` (wasm).
- **Voyager / any navigation lib** — no navigation is wired yet; `App.kt` is a placeholder.
  Navigation is an open choice (see roadmap), not an installed dependency.
- **SQLDelight** — see decision D3 (planned cache layer, not present).
- **media3 / palette** — the Android `PlatformPlayer` (ExoPlayer) and palette-based color
  extraction are not implemented yet, so these aren't imported. Add them in `androidMain` when
  that work lands.
- **Coil network fetcher** — `coil-compose` is declared, but no network fetcher is. Remote
  album art will not load until `io.coil-kt.coil3:coil-network-ktor3` (reuses the Ktor stack)
  is added. It is currently a beta (`3.5.0-beta01`), which is why it's deferred — not a bug.

### Goal #4 — "upgrade all dependencies to latest" (a *verified* pass)

This is its own task and **must not be done from memory**:

1. For every version, check the **current latest** on Maven Central / the library's release
   notes (use WebSearch/WebFetch). Do not assert "latest" without checking — versions in this
   file may already be post-cutoff for any given agent.
2. Respect the coupling rules above (Kotlin ↔ CMP ↔ Compose Compiler; AGP ↔ Kotlin).
3. **Converge shared versions across both repos** (Kotlin, coroutines, serialization, AGP).
   At minimum bring this repo up to the original's Kotlin `2.4.0` / AGP `9.2.0`, then apply
   whatever newer verified set both repos can share.
4. Validate in a **real toolchain** (Android SDK + JDK 17 + macOS/Xcode for iOS). A bump that
   only "looks right" here is unverified.

---

## Conventions

- **Kotlin official style** (`kotlin.code.style=official`).
- **Compose Multiplatform UI for all platforms.** No SwiftUI/native UI except platform chrome
  (Live Activity widget, floating window, notifications). iOS entry is
  `ComposeUIViewController { App() }` — the UI is Compose, not SwiftUI.
- **`org.jetbrains.compose.*` only in `commonMain`** — never `androidx.compose.*` directly,
  and never `LocalContext`/`LocalLifecycleOwner`/other `androidx.compose.ui.platform.Local*`
  (they don't exist off-Android). Use expect/actual wrappers.
- **State down via `StateFlow`/`collectAsState`, events up via lambdas.** No two-way binding.
- **DI: Koin.** Platform singletons (Ktor engine, `PlatformPlayer`, `PlatformSettings`) come
  from `platformModule()` actuals; load platform module before `sharedModule`.
- **HTTP: wrap every call in `safeApiCall { }`** (returns `T?`, swallows exceptions → `null`).
- **Serialization: kotlinx.serialization** with `ignoreUnknownKeys = true`, `isLenient = true`,
  `coerceInputValues = true`; `@SerialName` for divergent JSON keys (`ar`, `al`, …).
- **Do NOT reference** Room, Retrofit, OkHttp-as-API, Hilt, or Gson in shared code — they are
  Android-only and replaced by SQLDelight / Ktor / Koin / kotlinx.serialization. (OkHttp may
  appear only as a Ktor *engine* in `androidMain`.)
- **Preserve inline Chinese comments** — they carry porting intent from the original.

---

## UI design requirements (Material 3 Expressive)

Applies to all platforms, and the original Android repo is kept aligned (goal #3).

- **Color:** `surface` for page bases; `surfaceContainerHigh` / `surfaceContainerHighest` for
  tonal panels and list rows. Album-art–derived accents via `ImageColorExtractor` as gradient
  endpoints / highlights.
- **Shape — connected-list language:** large outer corners (~28dp), tight inner corners
  (~6dp), small vertical gaps (~1.5dp) between rows; implemented in
  `ui/component/Expressive.kt` (`connectedListItemShape()`). Interactive elements use pill
  (`RoundedCornerShape(50%)`) or `extraLarge` shapes.
- **Typography:** aggressive weight/size contrast — bold headlines (`DisplaySmall`,
  `HeadlineLarge`), regular body, lighter captions. Use the M3 Expressive type scale.
- **Prefer the real Expressive APIs** (`MaterialExpressiveTheme`, motion scheme) where the
  pinned `material3` version provides them, rather than only custom shapes on plain
  `MaterialTheme` (current `Theme.kt` is the latter — an approximation; upgrade it).
- **Per-screen:** NowPlaying (album-art hero gradient, oversized rounded cover ~252dp, bold
  marquee title); Playlist detail (album-color gradient header, pill "Play All", connected
  rows with download indicators); User page (blurred hero + avatar, badged playlists);
  Search (pill→bar shared-element transition, suggestion list); Settings (gradient hero, tonal
  rows, pill actions); Mini-player FAB (image-derived color w/ adaptive content luminance via
  `contrastingTextColor()`, oversized rounded shape).
- **Desktop floating window:** frameless/translucent, blur-behind, current lyric + mini art +
  compact controls, always-on-top toggle (part of the JVM `LyricNotificationController`).
- **iOS Live Activity:** Lock Screen + Dynamic Island — artwork thumbnail, title, artist,
  current lyric, synced to position.

---

## Build & run

Prereqs: JDK 17, Android SDK (compileSdk 36 / build-tools / minSdk 24), Xcode 16+ (iOS,
macOS only), IntelliJ IDEA / Android Studio with the KMP plugin.

```sh
# Android
./gradlew :androidApp:assembleDebug
# Desktop
./gradlew :desktopApp:run
./gradlew :shared:jvmTest
# iOS (macOS only) — then open iosApp/iosApp.xcodeproj in Xcode
./gradlew :shared:iosSimulatorArm64Test
# Web (after D2 — declaring the wasmJs target)
./gradlew :shared:wasmJsBrowserRun
# All tests
./gradlew :shared:allTests
```

`usesCleartextTraffic="true"` must be set in `androidApp`'s manifest (self-hosted HTTP API).

---

## Current divergence from this spec

This is the **only** section describing what's broken/missing. Treat it as the work queue.
Everything above describes the target; everything here is a gap to close.

### Build catalog — DONE + BUILD-VERIFIED (Android + Desktop, 2026-06-11)
- [x] `libs.versions.toml` + `shared/build.gradle.kts` declare every dependency the code imports
      (Ktor 3.5.0, kotlinx-serialization-json 1.11.0 + plugin, kotlinx-coroutines-core 1.11.0,
      Koin 4.2.1 + koin-android, Coil 3.5.0 coil-compose, datastore + androidx.core on androidMain,
      `compose.materialIconsExtended` + `compose.material3` on :desktopApp). Engines scoped per
      source set (okhttp/darwin/cio).
- [x] kotlinx-serialization Gradle plugin applied (root `apply false` + `:shared`).
- [x] Dead `shared/src/desktopMain/` deleted (D1).
- [x] **VERIFIED GREEN:** `:shared:compileKotlinJvm`, `:desktopApp:compileKotlin`,
      `:shared:jvmTest` (PlayQueueTest passes), `:shared:compileAndroidMain`,
      `:androidApp:compileDebugKotlin`, `:androidApp:assembleDebug` (APK). iOS not buildable here
      (Windows/no Xcode). The build caught several issues reading had missed — all fixed:
      KDoc `/*`, `defaultRequest` lacking `parameter()` (use `url.parameters.append`), missing
      `ImageColorExtractor` actuals, desktopApp missing material3, androidx.core too old for
      `setRequestPromotedOngoing`, and a `PlayQueueTest` `List<String>`/`List<String?>` inference.
- Note: `compose.materialIconsExtended` is deprecated (pinned 1.7.3, no updates) — works for now;
  migrate to Material Symbols / vector resources eventually.

### Goal #4 toolchain convergence — Kotlin DONE+verified; AGP deferred
- [x] **Kotlin `2.3.21 → 2.4.0`** (+ CMP `1.11.0 → 1.11.1`) — bumped and **build-verified** on
      Android + Desktop (with AGP 9.0.1 / Gradle 9.1.0). Kotlin is now converged with the original.
- [ ] **AGP `9.0.1 → 9.2.0` deferred.** Empirically (2026-06-11): AGP 9.2.0 needs Gradle ≥ 9.4.1;
      after bumping the wrapper, it then fails to resolve `com.android.tools.build:aapt2:9.2.0` and
      breaks configuration-cache serialization of `MergeResources`. Reverted to the green
      9.0.1/9.1.0. Revisit when those AGP-9.2 issues clear (or disable config cache to retry).

### Dead / leftover code
- [ ] **`Greeting.kt` / `GreetingUtil.kt`** and the `compose-multiplatform.xml` template
      drawable are KMP-template leftovers — remove once `App.kt` is wired to real screens.
- [ ] **`wasmJsMain/` is dormant** (D2: target intentionally not declared; web is P3). Its
      `Platform.wasm.kt` and `WasmNotificationStub` reference an interface shape that doesn't
      exist (`Platform.isDesktop/isMobile`) — they must be reconciled with the real
      `Platform` interface (which currently has only `name`) **and** the wasm deps added
      (`ktor-client-js`, `kotlinx-browser` for `localStorage`) when web is enabled.

### Latent runtime gaps (compile fine; will surface when exercised)
- [x] **HttpClient base URL / cookie wired** (2026-06-11) — every `platformModule()` now builds
      its client via `HttpClientFactory.create(baseUrl, realIP, cookie, engine)`, sourcing
      `baseUrl` (SettingKeys.SERVER, default `http://ncm.tryagain.icu/`) and `cookie`
      (SettingKeys.COOKIE) from `PlatformSettings`. `HttpClientFactory.create` was completed to
      port the original's interceptor (base URL + `realIP` 192.168.1.1 + `timestamp` cache-buster
      via Ktor `getTimeMillis()` + cleaned cookie header). Remaining nuances: (a) the client is a
      Koin singleton built once, so changing server/cookie takes effect next launch (matches the
      original `RetrofitInstance` object); (b) cookie is attached whenever non-empty rather than
      skipped on `/login/*` (Ktor `defaultRequest` can't see the per-request path) — refine with a
      send-pipeline interceptor if strict per-path skipping is needed.
- [ ] **`kotlinx-coroutines-swing` is only in `desktopApp`, not `shared/jvmMain`.**
      `DesktopFloatingWindowController` uses `Dispatchers.Main`; running it via `:shared:jvmTest`
      will throw "Module with the Main dispatcher is missing". Add `kotlinx-coroutinesSwing` to
      `jvmMain` (or inject a dispatcher) before testing JVM player/notification logic.
- [ ] **No Coil network fetcher** → `AsyncImage` won't load remote album art until
      `coil-network-ktor3` is added (see dependency section).
- [ ] **`Platform` interface has only `name`.** If form-factor branching is wanted, add
      `isDesktop`/`isMobile` (with defaults) to the interface and update all actuals — the
      removed `desktopMain` and the dormant wasm code assumed these existed.

### Misplaced / misnamed
- [x] **`MediaControlsIntegrator` moved to commonMain** (2026-06-11) — now in
      `commonMain/smtc/MediaControls.kt`; the misnamed `jvmMain/.../WindowsSmtcIntegration.kt` was
      deleted and replaced by `jvmMain/smtc/WindowsMediaControls.kt` (the OS binding home).

### Implemented as stub / not wired
- [~] **`App.kt` now renders the real screen** (2026-06-11) — `App()` is
      `RedefineNCMTheme { Surface { NowPlayingScreen() } }` (no more "Click me!" template).
      DI graph is now both defined *and* initialised; **build-unverified** but reasoning-verified:
      - [x] **PlatformPlayer binding** — `sharedModule` binds `InMemoryPlatformPlayer` (pure-Kotlin
        reference player over the tested `PlayQueue`; no real audio). Real media3/AVPlayer/JVM
        players replace it in `platformModule()` later.
      - [x] **Fixed latent DI bug** — `NowPlayingViewModel` was bound with 3 `get()`s but its 3rd
        param `lyricBus = LyricBus` is an object default → now 2 `get()`s.
      - [x] **`initKoin()` wired in all entry points** — idempotent `initKoin(config)` in
        `di/Modules.kt` (guards on `GlobalContext.getOrNull()`). Desktop `main.kt` and iOS
        `MainViewController` call `initKoin()`; Android `RedefineNCMApp : Application` calls
        `initKoin { androidContext(this) }` (so `PlatformSettings(get())` resolves the Context) +
        `LyricNotificationController.init(...)`. Added `koin-android` to `:androidApp`; registered
        the Application + INTERNET/POST_NOTIFICATIONS perms + `usesCleartextTraffic` in the manifest.
      - **BUILD-VERIFIED on Android + Desktop (2026-06-11):** `:androidApp:assembleDebug` (APK),
        `:desktopApp:compileKotlin`, `:shared:jvmTest` all green — the DI graph (incl. koin-compose
        global-context fallback) compiles/assembles; the Android Application + manifest are valid.
        **iOS path unverified** (no Mac). Now (2026-06-11): **Home is the entry** + a hand-rolled
        back-stack nav (`App.kt`) with Home / Search / PlaylistDetail / Login / NowPlaying screens,
        all M3 Expressive and build-verified. Remaining: (1) User + Settings screens + QR-login UI;
        (2) RUN it (`:desktopApp:run`, GUI) to confirm runtime DI/render; real per-platform audio
        backends (media3/AVPlayer/JVM) still replace `InMemoryPlatformPlayer`.
- [~] **Windows SMTC — pipeline wired (compile-verified), native OS binding TODO** (2026-06-11).
      `MediaControlMetadata` + `MediaControlsIntegrator` are in `commonMain/smtc/MediaControls.kt`;
      `NowPlayingViewModel` feeds it (title/artist/album/duration/isPlaying); `jvmMain/smtc/
      WindowsMediaControls` observes it (started from `desktopApp/main.kt`). The final OS push is a
      no-op pending a **native WinRT helper DLL** (SMTC has no runtime-verifiable JVM/JNA-WinRT
      path) — precise recipe is in the `WindowsMediaControls` KDoc. Linux MPRIS / macOS
      MPNowPlayingInfoCenter are analogous, also TODO.
- [~] **iOS Live Activity — Swift source written** (2026-06-11), Xcode target wiring is the only
      remaining (build-gated) step. Added: `iosApp/LyricWidget/` (`LyricActivityAttributes`,
      `LyricLiveActivity` = Lock Screen + Dynamic Island UI, `LyricWidgetBundle`, `Info.plist`),
      `iosApp/iosApp/LiveActivityManager.swift` (drives `Activity.request/update/end` from the
      Kotlin stream), `iOSApp.swift` starts it, app `Info.plist` sets `NSSupportsLiveActivities`.
      Kotlin `LyricNotificationController` gained `startObserving/stopObserving` for Swift. Uses
      ActivityKit `ContentState` (no App Group needed for text; artwork-in-LA = App-Group TODO).
      **Remaining:** add the Widget Extension target in Xcode + target memberships — see
      `iosApp/LyricWidget/SETUP.md`. Like all platform now-playing surfaces, it only shows once
      the playback/lyric pipeline calls `LyricNotificationController.updateLyric(...)`.
- [x] **Desktop floating window is wired** (2026-06-11) — `desktopApp/main.kt` now opens a
      second frameless / translucent / always-on-top Compose window that renders
      `DesktopFloatingWindowController`'s `floatingLyricData` when `isWindowVisible` is true.
      Remaining: the playback/lyric pipeline must call `LyricNotificationController.show()` +
      `updateLyric(...)` to populate it (today nothing drives it, so it stays hidden).
- [x] **`Theme.kt` now uses real `MaterialExpressiveTheme`** (2026-06-11) with
      `MotionScheme.expressive()` + the Expressive shape/type scales. Remaining: dynamic /
      album-art–derived color schemes via an expect/actual provider (`dynamicColorScheme` is
      Android-only).

### Not started
- [ ] **SQLDelight cache** (D3): plugin, `.sq` schema, `DatabaseDriverFactory` expect/actual,
      and cache-then-network in `Repository`.
- [x] **`PlayQueue` + `PlayQueueTest`** (shuffle regression suite) — DONE (2026-06-11), pure
      Kotlin, fully reasoned (build-unverified like everything here). Next: have the real
      `PlatformPlayer` actuals + `NowPlayingViewModel` delegate to `PlayQueue`.
- [ ] Domain model layer (DTO → domain mapping), if desired.
- [ ] Voyager navigation wiring (confirm whether Voyager is the chosen nav or remove it).
- [ ] Cookie injection in Ktor (`HttpClientFactory` receives `cookie` but does not yet inject
      it per request — port the original's interceptor behavior).
- [ ] Goal #4 verified dependency upgrade + cross-repo version convergence.
- [ ] CI pipeline.

> **When in doubt, the source on disk is the truth about _what exists_; this document is the
> truth about _what it should become_.** If they conflict on a fact (e.g., a version number),
> trust the file and fix this doc; if they conflict on a decision (e.g., source-set naming),
> trust this doc and fix the code.
