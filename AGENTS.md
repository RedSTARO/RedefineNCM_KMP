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

## 实操约束（Git）

- 遇到分支同步/提交历史冲突时，优先使用 `git rebase` 处理冲突，不允许默认走 `git merge` 方式。未明确要求保留合并提交时，统一走 rebase 方案。

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
| **Web / WASM** | P3 (enabled) | Browser audio + Media Session, OPFS offline downloads, in-page/system lyric surface |

The app talks to a self-hosted
[NeteaseCloudMusicApi](https://github.com/Binaryify/NeteaseCloudMusicApi)-style backend
(base URL is user-configurable in Settings). All API communication is JSON over HTTP,
typically against a server on the same LAN or localhost (so cleartext HTTP must be allowed).

### Active goals driving current work

1. Keep all four enabled targets behaviorally aligned as common features evolve.
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

### D2 — `wasmJs` browser target is enabled — **DONE (updated 2026-07-11)**

`shared/build.gradle.kts` declares `wasmJs { browser(); binaries.executable() }` and compiles
`wasmJsMain/` as the fourth production target. Web uses the same common Compose screens,
navigation, repository, queue invariant, settings and download orchestration as the native
targets. Platform actuals provide `HTMLAudioElement` playback, Media Session transport controls,
browser-history back handling, browser-storage-backed SQLDelight queries, OPFS downloads and
offline playback, palette extraction, settings import/export, and a visible lyric surface. Web
also bundles Noto Sans SC and preloads it before exposing the Compose canvas, because CanvasKit
cannot rely on host CJK fonts for dynamic Chinese song titles and lyrics.

Page lifecycle is a locked behavior: `visibilitychange` to hidden, `pagehide`, and
`beforeunload` must pause playback. Pausing must also invalidate an in-flight stream-URL lookup,
otherwise a late response can start audio after the page has been left.

### D3 — Local cache is SQLDelight — **DONE (updated 2026-07-13)**

The original caches via Room (cache-then-network). The KMP replacement is **SQLDelight**, now
fully wired: plugin + 10 `.sq` tables under `shared/src/commonMain/sqldelight/` (user detail /
user level / user playlist / playlist detail / playlist tracks / recommend ×2 / lyric / comment /
**PlayerStatus**) + `DatabaseDriverFactory` expect/actuals (android/native/sqlite/browser storage). `Repository`
implements cache-then-network for all cached endpoints and persists/restores the play queue
via the `PlayerStatus` table. Schema version 3 includes formal `1.sqm` and `2.sqm` migrations
for the playlist detail/tracks, comment, player-status, and user-level tables; platform drivers must use
`AppDatabase.Schema` migrations rather than ad-hoc `onOpen` table creation.

### D4 — Notification / now-playing surfaces are one common contract, four platform actuals

`commonMain` owns the contract; each platform renders it natively:

- **`notification/LyricNotificationController`** — `expect object` with
  `updateLyric(title, artist, currentLyric, nextLyric, artworkUri, isPlaying, positionMs, durationMs)`, `clearFocus()`,
  `reset()`. This is the **lyric display** surface.
- **`smtc/MediaControlsIntegrator`** — common `object` holding `MediaControlMetadata`
  (title/artist/album/artwork/duration/position/isPlaying) for OS media controls. This is
  the **transport controls** surface. Windows-specific WinRT code lives separately in
  `jvmMain/smtc/WindowsMediaControls.kt`.

Platform actuals:

| Platform | Lyric surface (`LyricNotificationController`) | Transport surface |
|---|---|---|
| Android | Live-update `Notification` (MediaStyle / custom RemoteViews) on a channel | Media3 `MediaSession` |
| iOS | ActivityKit **Live Activity** → Dynamic Island + Lock Screen | `MPNowPlayingInfoCenter` / `MPRemoteCommandCenter` |
| Desktop (JVM) | Frameless, always-on-top **Compose floating window** | **Windows SMTC** (JNA/COM); **MPRIS** (D-Bus) on Linux |
| Web (WASM) | In-page lyric pill + document title; system notification when permission is already granted | Browser Media Session metadata/actions + `HTMLAudioElement` |

**iOS bridge pattern (updated decision, 2026-07-10):** Kotlin does **not** call ActivityKit
directly. `LyricNotificationController` exposes `LiveActivityData` to the Swift main-app
bridge, which serially starts/updates/ends the ActivityKit activity. The `LyricWidget`
extension renders the resulting `ContentState`; it is wired into the Xcode project. Text does
not require an App Group. An App Group is only needed if artwork is later shared with the
extension through a file cache.

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

### Application versioning

App version metadata is Git-derived from `gradle/app-version.gradle.kts`. The required base
tag format is `v<major>.<minor>.<patch>`; the current base tag is `v0.0.1`. Full app version
names are `baseTag.shortCommitHash`, e.g. `v0.0.1.cf6cea74`. Build numbers are
`git rev-list --count HEAD`.

Platform mapping:

- Android: `versionName = full app version`, `versionCode = commit count`.
- Shared runtime: generated common `BuildInfo` exposes base tag, semantic base version,
  commit hash, commit-count build number, and full app version.
- Desktop native distributions: `packageVersion = base semantic version` (without `v`), because
  native packagers require numeric package versions; runtime code still uses full `BuildInfo`.
- iOS app + LyricWidget: `CFBundleShortVersionString = base semantic version`,
  `CFBundleVersion = commit count`, and `RedefineNCMVersionName = full app version`. Xcode build
  phases run `iosApp/Scripts/stamp-version.sh` to stamp the built product from the same Git data.

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
> KDoc, `parameter()` not on `defaultRequest`, missing platform color extraction actuals, etc.). Do NOT
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
│       │   ├── App.kt                   # Root composable + hand-rolled back-stack nav
│       │   ├── Platform.kt              # expect fun getPlatform(): Platform
│       │   ├── data/
│       │   │   ├── Repository.kt        # Cache-then-network SQLDelight repository + write Results
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
│       │   ├── recognition/
│       │   │   ├── AudioFingerprint.kt   # Pure-Kotlin fingerprint extractor + codec
│       │   │   ├── MicrophoneRecorder.kt # common capture contract
│       │   │   └── PcmResampler.kt       # platform PCM → 8 kHz mono
│       │   ├── smtc/
│       │   │   └── (MediaControlsIntegrator)        # ⚠ currently in jvmMain, not commonMain — see divergence
│       │   ├── util/
│       │   │   ├── LyricParser.kt        # Pure-Kotlin LRC parser
│       │   │   ├── CoilImageTheme.kt      # expect fun themeColorFromCoilImage — album-art accent
│       │   │   └── Settings.kt           # expect class PlatformSettings + SettingKeys
│       │   ├── viewmodel/
│       │   │   ├── LoginViewModel.kt
│       │   │   ├── MainViewModel.kt
│       │   │   ├── NowPlayingViewModel.kt # holds shuffle invariant (rebuildPlaylistFromTimeline)
│       │   │   └── SongRecognitionViewModel.kt
│       │   └── ui/
│       │       ├── screen/FullLyricScreen.kt     # Compose fallback for the full-screen player
│       │       ├── screen/SongRecognitionScreen.kt
│       │       ├── component/Expressive.kt      # connected-list shapes
│       │       └── theme/{Color,Type,Theme}.kt  # real M3 Expressive theme + image-derived palettes
│       ├── androidMain/  …/Platform.android.kt, notification/AndroidNotificationController.kt,
│       │                  di/AndroidPlatformModule.kt, util/AndroidSettings.kt,
│       │                  recognition/AndroidMicrophoneRecorder.kt,
│       │                  player/ExoPlayerPlatformPlayer.kt (Media3/ExoPlayer PlatformPlayer impl),
│       │                  player/RedirectingDataSource.kt (placeholder URI → CDN URL resolver)
│       ├── iosMain/      …/Platform.ios.kt, MainViewController.kt,
│       │                  notification/IosLiveActivityController.kt (Live Activity data bridge),
│       │                  di/IosPlatformModule.kt, util/IosSettings.kt,
│       │                  recognition/IosMicrophoneRecorder.kt
│       ├── jvmMain/      …/Platform.jvm.kt, notification/DesktopFloatingWindowController.kt,
│       │                  smtc/WindowsMediaControls.kt (native WinRT SMTC binding),
│       │                  di/JvmPlatformModule.kt, util/JvmSettings.kt,
│       │                  recognition/JvmMicrophoneRecorder.kt
│       ├── wasmJsMain/   …/main.kt, Platform.wasm.kt, player/WebPlatformPlayer.kt,
│       │                  notification/, data/db/BrowserStorageSqlDriver.kt,
│       │                  di/WasmPlatformModule.kt, util/{WasmSettings,WebDownloadStorage}.kt,
│       │                  recognition/WasmMicrophoneRecorder.kt
│       ├── commonTest/   Shared business, API, player, recognition and UI regression tests
│       └── jvmTest/      JVM database, media-control and platform integration tests
├── androidApp/    AGP application; MainActivity; PlaybackService (MediaSessionService);
│                  RedefineNCMApp; depends on :shared + media3 directly
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

The only full-screen playback route is `WebViewLyricScreen`: Android and supported Windows
Desktop hosts render AMLL, while other JVM platforms, iOS, and Web use `FullLyricScreen` as
the Compose fallback. `MiniNowPlayingBar`, the
Desktop playback strip, and OS/deep-link now-playing requests must open this route directly.
The former common `NowPlayingScreen` has been removed and must not be restored as a parallel
player page.

### Playback reporting contract

`PlaybackReportingCoordinator` is an eager process-wide common singleton. Platform players do
not call reporting endpoints directly; they publish a monotonic `playbackOccurrence` whenever a
real item selection occurs. Queue replacement, manual/natural track transitions, and replay after
`ENDED` advance it exactly once. Pause/resume, seek within the current item, shuffle changes, and
queue append do not.

For each actually playing selection, the coordinator generates one 12-character uppercase
alphanumeric relay session ID and calls `/relay/play/state/submit` at start, on changed
pause/mode/final state, and every 30 seconds while playing. `playMode` is `random` under shuffle
and otherwise `list_loop`; `type` is `song`. Relay has a bounded latest-state queue, while each
one-shot account action dispatches independently, so a 60-second relay timeout cannot block
recent-play or half-play reporting or create an unbounded telemetry queue. Structured relay results remain visible;
an unmistakable HTML route 404 is cached as unsupported, while JSON 404 and other server bodies
remain server rejections rather than permanently disabling the route.

As soon as a selection is stably and actually `PLAYING`, the coordinator sends one `startplay`
action through `POST /weblog` so it enters the account's recent plays. Pause/resume, seek and
shuffle changes do not repeat it; a new `playbackOccurrence` does. Playlist `sourceId` is used when
it is a positive integer, otherwise the song ID is the source fallback. Only this action-bound
Cookie snapshot derives `os=osx`; settings are never rewritten. This is a dedicated start event,
not a fallback from `/scrobble/v1`, and a failed or ambiguous request is not retried.

`/scrobble/v1` is attempted at most once per playback session after verified position progress
reaches half of the known whole-second duration; unknown duration uses 30 seconds. Position
progress is bounded by monotonic elapsed time so seeks, buffering, stalled playback, and system
sleep do not manufacture listening time. A failed or ambiguous scrobble is not retried because
the backend can partially accept PLV before PLD fails and exposes no idempotency key. Scrobble uses
only `/scrobble/v1`; an HTML route 404 is reported as unsupported and does not trigger another
reporting endpoint. The upstream route also emits PLV before PLD, so it may advance the same song's
recent-play timestamp again at half-play; current backend behavior still needs a real-account
verification. Playlist items carry and persist `sourceId`; `source` remains the backend default `list`.

Reporting actions bind the cleaned Cookie snapshot from session creation, then re-check the current
account before dispatch and after every readback. Status is credential-scoped and carries a monotonic
reporting generation, so a previous account or an older replay of the same song cannot overwrite the
current result. Accepted scrobbles trigger bounded before/after reads of `/user/record`,
`/record/recent/song`, and `/user/level`; the refreshed level replaces both the SQLDelight cache and
the current account UI snapshot. Only target-song record appearance/play-count growth or target-song
recent-play movement proves account-side accounting. Level count and record score are supplemental
diagnostics, not standalone proof. The current public backend exposes relay submit but no relay pull;
history endpoints do not contain `progress`, `sessionId`, or `playMode`, so precise inbound playback
roaming must remain unavailable rather than being inferred from recent history. `NOT_REFLECTED` is a
conservative bounded-window result, not proof that an asynchronously processed report will never land.

### Intelligence playback contract

The current account's own `specialType == 5` playlist is its liked-music playlist; both that
playlist and the legacy standard-Chinese-name fallback must have `creator.userId` equal to the
current UID. Its trailing heart is an independent action, while clicking the rest of the row
continues to open playlist detail. The action selects a random positive song ID from `/likelist`,
then calls `/playmode/intelligence/list` with that ID and the liked playlist ID. `sid` is omitted
because the backend already defaults `startMusicId` to `id`.

Intelligence responses are dynamic and are not persisted in SQLDelight. Valid `songInfo` entries
keep server order, are de-duplicated by song ID, carry the liked playlist ID as `sourceId`, and
replace the current queue at index zero. Ordinary shuffle must be disabled before queue replacement:
the server has already ordered the recommendations, and a second shuffle would corrupt that order
and misreport the relay mode as `random`. Empty/failed responses must leave the existing queue
unchanged. Account refresh or switching cancels the in-flight request.

### Song recognition contract

The Home “音乐工具” card opens the shared Compose `SongRecognitionScreen`. Recognition is
implemented in Kotlin source on every target: Android `AudioRecord`, iOS `AVAudioEngine`, JVM
Java Sound, and Web `getUserMedia` / `MediaRecorder` / Web Audio through typed Kotlin/Wasm
`external` declarations. The recognition path adds no handwritten JavaScript or TypeScript.

Each attempt produces exactly three seconds of mono PCM for common processing.
`prepareRecognitionSamples()` resamples it to 24,000 Float32 samples at 8 kHz, then
`AudioFingerprint` generates the native fingerprint. The algorithm is derived from Open Orpheus;
preserve the attribution and MIT notice in `THIRD_PARTY_NOTICES.md`.

`NCMApi.audioMatch()` sends `POST /audio/match` with query parameters `duration=3` and
`audioFP=<base64 fingerprint>` to the configured backend. `data.type == 1` carries matched
`result` entries; `data.type == 0` is a valid no-match response. Missing `data`, unknown types,
or empty matched results are protocol errors. Matched songs use the legacy fields `artists`,
`album`, and `duration`. `audioFP` is microphone-derived sensitive data and must remain redacted
from HTTP logs.

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
| Web runtime | `kotlinx-browser 0.5.0` + Ktor JS engine `3.5.0` | wasmJsMain |
| Desktop native APIs | JNA `5.14.0` + dbus-java `5.2.0` | jvmMain |
| Android settings | `androidx.datastore:datastore-preferences 1.2.0` | androidMain |
| Android audio | `media3-exoplayer 1.10.1` + `media3-session 1.10.1` | androidMain + :androidApp |

**Status update (2026-07-04):**
- **`multiplatform-settings`** — still deliberately NOT a dependency; `PlatformSettings` is a
  hand-rolled `expect class` (DataStore / `NSUserDefaults` / JVM prefs / `localStorage`).
- **Voyager / any navigation lib** — still not a dependency; `App.kt` uses a hand-rolled
  tab + push-stack navigation (3 tabs, NavigationRail on ≥600dp) — this is the decided approach.
- **SQLDelight `2.3.2`** — now present (see D3).
- **`androidx.palette:palette-ktx 1.0.0`** — now present in `androidMain`; drives
  `themeColorFromCoilImage()` (muted → vibrant → dominant, same as the original ImageParser).
- **`coil-network-ktor3`** — now present; remote album art loads via the Ktor stack
  (`RedefineNCMApp` registers `KtorNetworkFetcherFactory`).

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

- **Commit messages:** When creating commits or rewriting existing commit messages, use the
  Conventional Commit style below. Header is required. Body and Footer are optional. Commit
  message prose MUST be English.
- Header format MUST be `<type>(<scope>): <subject>`, or `<type>: <subject>` when scope is
  omitted.
  - `type` is required and MUST be one of: `feat`, `fix`, `docs`, `style`, `refactor`,
    `perf`, `test`, `chore`, or `revert`.
  - `scope` is optional and names the affected module or feature.
  - `subject` is required, should be concise, and should usually stay within 50 characters.
- If Body is present, separate it from the Header with one blank line. Use it to explain the
  motivation, concrete changes, and behavior differences from the previous version.
- If Footer is present, separate it from the Body, or from the Header when Body is omitted,
  with one blank line. Use it for Breaking Changes and closed issue references such as
  `Closes #123`.
- Do not treat Body or Footer as mandatory. Do not reinterpret "English" as "ASCII-only"
  unless the user explicitly asks for that stricter rule.
- Example:
  ```text
  feat(user): add user registration

  Implement registration by email and phone number.
  Tighten user data validation.

  Closes #456
  ```
- If asked to fix non-compliant commit messages, rewrite only the messages that violate this
  rule and preserve the committed file trees.
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
  tonal panels and list rows. Album-art–derived accents via `themeColorFromCoilImage()` as
  gradient endpoints / highlights; extraction runs off the UI thread and is keyed by artwork.
- **Shape — connected-list language:** large outer corners (~28dp), tight inner corners
  (~6dp), small vertical gaps (~1.5dp) between rows; implemented in
  `ui/component/Expressive.kt` (`connectedListItemShape()`). Interactive elements use pill
  (`RoundedCornerShape(50%)`) or `extraLarge` shapes.
- **Typography:** aggressive weight/size contrast — bold headlines (`DisplaySmall`,
  `HeadlineLarge`), regular body, lighter captions. Use the M3 Expressive type scale.
- **Use the real Expressive APIs** (`MaterialExpressiveTheme`, motion scheme) provided by the
  pinned `material3` version; custom shapes and page palettes extend that theme rather than
  replacing it.
- **Per-screen:** Full-screen player (AMLL on Android/supported Windows Desktop,
  `FullLyricScreen` fallback on other JVM platforms/iOS/Web, with the shared auto-hiding
  playback console); Playlist detail (album-color gradient header, pill "Play All", connected
  rows with download indicators); User page (blurred hero + avatar, badged playlists);
  Search (pill→bar shared-element transition, suggestion list); Downloads (queue summary hero,
  progress rows, pause/resume/cancel/retry controls); Settings (gradient hero, tonal rows, pill
  actions); Mini-player FAB (image-derived color w/ adaptive content luminance via
  `contrastingTextColor()`, oversized rounded shape).
- **Desktop floating window:** frameless/translucent, blur-behind, current lyric + mini art +
  compact controls, always-on-top toggle (part of the JVM `LyricNotificationController`).
- **iOS Live Activity:** Lock Screen + Dynamic Island — artwork thumbnail, title, artist,
  current lyric, synced to position.

---

## Build & run

Prereqs: JDK 17, Android SDK (compileSdk 36 / build-tools / minSdk 24), Xcode 16+ (iOS,
macOS only), IntelliJ IDEA / Android Studio with the KMP plugin. Web browser tests require a
locally available Chrome/Chromium-compatible headless browser.

```sh
# Android
./gradlew :androidApp:assembleDebug
# Desktop
./gradlew :desktopApp:run
./gradlew :shared:jvmTest
# iOS (macOS only) — then open iosApp/iosApp.xcodeproj in Xcode
./gradlew :shared:iosSimulatorArm64Test
# Web development server
./gradlew :shared:wasmJsBrowserDevelopmentRun
# Web browser tests + production static distribution
./gradlew :shared:wasmJsBrowserTest :shared:wasmJsBrowserDistribution
# All tests
./gradlew :shared:allTests
```

`usesCleartextTraffic="true"` must be set in `androidApp`'s manifest (self-hosted HTTP API).

The Web production distribution is emitted at
`shared/build/dist/wasmJs/productionExecutable/`. Deploy it as a static site with the correct
`.wasm` MIME type. Browser networking requires CORS from the configured API/audio/image hosts;
an HTTPS page cannot call HTTP resources. Fetch cannot set a `Cookie` header, so Web sends the
cleaned cookie through the API's `cookie` query parameter. OPFS downloads require HTTPS or
`localhost` plus browser OPFS support. Browser autoplay, Media Session and Notification support
remain browser policy/capability boundaries; rejected autoplay leaves the player paused, and
lyrics remain visible in-page even when system notification support is unavailable.
Web microphone access for song recognition requires HTTPS or `localhost` plus site microphone
permission. An HTTPS deployment also cannot call an HTTP `/audio/match` backend; the backend must
use HTTPS and allow the Web origin through CORS.

---

## Current divergence from this spec

This is the **only** section describing what's broken/missing. Treat it as the work queue.
Everything above describes the target; everything here is a gap to close.

### Feature-parity pass vs the original app — DONE + BUILD-VERIFIED (2026-07-04)
Full file-by-file audit against `../RedefineNCM` (frozen 2026-06-12), then closed every common
feature gap; platform integrations use target-specific actuals:
- **fetchUID** now really calls `/user/account` and caches the UID (was a stub that never
  fetched); `refreshAccount()` clears it on account switch.
- **QR login fix**: the QR PNG bytes were never assigned → image never showed; now decoded
  from base64 locally.
- **Shuffle invariant restored**: `ExoPlayerPlatformPlayer.rebuildQueue()` walks the timeline
  in *play order* (`getFirstWindowIndex`/`getNextWindowIndex`), `skipToIndex` maps play-order →
  window index, and transitions/shuffle-toggles/timeline changes all trigger the single rebuild
  path; `NowPlayingViewModel` now collects `queue`/`currentIndex` live.
- **Player-status persistence**: `PlayerStatus.sq` + `Repository.get/savePlayerStatus` +
  `PlatformPlayer.restoreQueue` (no autoplay) + save on `MainActivity.onPause`.
- **Download manager**: `SongDownloadManager` owns the common queue/state model. The Downloads
  page remains as an internal route (pause/resume/cancel/retry/clear/delete local files), but
  the common tab/sidebar entry is removed; on Android, `AndroidDownloadService` runs downloads as
  a foreground data-sync service and its notification is the entry point into that page. It stores
  the actual downloaded quality from
  `/song/url/v1`'s returned `level` and prefetches/caches lyrics in SQLDelight after the audio file
  is written. Local-library sync is snapshot-based, not ID-only: platform scans return
  `DownloadedSongSnapshot` (song ID, file name, URI, size, modified time), `DownloadedSongsCache`
  caches that snapshot for O(1) row indicators, and `SongDownloadManager.syncWithLocalLibrary()`
  reconciles queued tasks with real files while importing disk-only downloads as Completed rows
  with best-effort `/song/detail` metadata. `SongDownloader` actuals only stream files to the
  platform download folder. Android writes `Downloads/RedefineNCM/` through `MediaStore` instead of
  the system `DownloadManager`; JVM writes `~/Music/RedefineNCM/`; iOS streams into
  `Documents/RedefineNCM/` and uses an NSURLSession background transfer for durable downloads.
- **Playlist behaviors**: `replacePlaylist` setting honored on song click,
  `playlistUpdatePlaycount` reported, no auto-jump to the full-screen player (original behavior).
- **Album-art theme color**: `themeColorFromCoilImage()` expect/actual (Android Palette /
  JVM Skia sampling / iOS stub) wired into MiniNowPlayingBar (with luminance-adaptive content
  color + spring animation), PlaylistDetail hero, and the full-screen player fallback.
- **Search shared-element transition** (pill → bar) via `SharedTransitionLayout` in HomeScreen.
- **Responsive nav**: NavigationRail on ≥600dp; no-cookie startup routes to Login.
- **Settings**: server availability check (`/inner/version/`); the legacy-persisted
  `adaptOriginalAndroidLyric` value now controls the optional Android Live Update notification
  and Desktop floating-lyrics window (default off, immediate enable/disable); iOS Live Activity
  and Web lyrics remain independent. The live-update notification uses the lyric as its title.
- **Update check on launch** (checkUpdate setting → GitHub releases/latest → Snackbar).
- Skipped intentionally: `HiddenTestActivity`, `serverMocker` (dev tools), `dailysignin`
  (declared but never called in the original either).

### Build catalog — DONE + BUILD-VERIFIED (Android + Desktop + Web, updated 2026-07-11)
- [x] `libs.versions.toml` + `shared/build.gradle.kts` declare every dependency the code imports
      (Ktor 3.5.0, kotlinx-serialization-json 1.11.0 + plugin, kotlinx-coroutines-core 1.11.0,
      Koin 4.2.1 + koin-android, Coil 3.5.0 coil-compose, datastore + androidx.core on androidMain,
      `compose.material3` on :desktopApp, and the Web browser dependencies). Engines are scoped
      per source set (OkHttp on Android/JVM, Darwin on iOS, JS/Fetch on Web).
- [x] kotlinx-serialization Gradle plugin applied (root `apply false` + `:shared`).
- [x] Dead `shared/src/desktopMain/` deleted (D1).
- [x] **VERIFIED GREEN:** `:shared:compileKotlinJvm`, `:desktopApp:compileKotlin`,
      `:shared:jvmTest` (PlayQueueTest passes), `:shared:compileAndroidMain`,
      `:androidApp:compileDebugKotlin`, `:androidApp:assembleDebug` (APK). iOS not buildable here
      (Windows/no Xcode). The build caught several issues reading had missed — all fixed:
      KDoc `/*`, `defaultRequest` lacking `parameter()` (use `url.parameters.append`), missing
      platform color extraction actuals, desktopApp missing material3, androidx.core too old for
      `setRequestPromotedOngoing`, and a `PlayQueueTest` `List<String>`/`List<String?>` inference.
- [x] **Web verified locally:** `:shared:compileKotlinWasmJs`,
      `:shared:wasmJsBrowserTest`, and `:shared:wasmJsBrowserDistribution` pass on Windows;
      Chrome Headless covers SQL persistence, page-exit lifecycle, and a real OPFS
      download/scan/blob-URL/delete round trip. The same tasks are enforced by `build-web` CI.
- [x] Deprecated `compose.materialIconsExtended` removed; shared self-drawn Material Symbols are
      used instead (2026-07-04).

### Goal #4 toolchain convergence — Kotlin DONE+verified; AGP deferred
- [x] **Kotlin `2.3.21 → 2.4.0`** (+ CMP `1.11.0 → 1.11.1`) — bumped and **build-verified** on
      Android + Desktop (with AGP 9.0.1 / Gradle 9.1.0). Kotlin is now converged with the original.
- [ ] **AGP `9.0.1 → 9.2.0` deferred.** Empirically (2026-06-11): AGP 9.2.0 needs Gradle ≥ 9.4.1;
      after bumping the wrapper, it then fails to resolve `com.android.tools.build:aapt2:9.2.0` and
      breaks configuration-cache serialization of `MergeResources`. Reverted to the green
      9.0.1/9.1.0. Revisit when those AGP-9.2 issues clear (or disable config cache to retry).

### Dead / leftover code
- [x] **`Greeting.kt` / `GreetingUtil.kt` + `compose-multiplatform.xml` deleted** (2026-06-13) — template stubs removed; no references existed.
- [x] **Unused `ImageColorExtractor` expect/actual chain, template tests, duplicate YRC/download
      reconciliation code, and tracked runtime artifacts removed** (2026-07-10).
- [x] **`wasmJsMain/` enabled and completed** (2026-07-11) — target, entry point, dependencies,
      real browser player, Media Session, local persistence, OPFS download/offline playback,
      platform actuals, browser tests and production distribution are wired. Page hiding/exiting
      pauses playback and cancels pending stream resolution.

### Latent runtime gaps (compile fine; will surface when exercised)
- [x] **HttpClient base URL / cookie wired** (updated 2026-07-11) — every `platformModule()` now builds
      its client via `HttpClientFactory.create(baseUrl, realIP, cookieProvider, engine)`, sourcing
      `baseUrl` (SettingKeys.SERVER, default `https://ncm.tryagain.icu/`) and `cookie`
      (SettingKeys.COOKIE) from `PlatformSettings`. `HttpClientFactory.create` was completed to
      port the original's interceptor (base URL + `realIP` 192.168.1.1 + `timestamp` cache-buster
      via Ktor `getTimeMillis()` + cleaned cookie). The base URL is fixed when the Koin singleton
      is created, so changing the server takes effect next launch; the cookie provider is read on
      every request, so login/logout applies immediately. Native targets send the cookie header;
      Web uses the API-compatible `cookie` query parameter because Fetch forbids that header.
      A non-empty cookie is attached to login paths too, matching the existing port behavior.
- [x] **`kotlinx-coroutines-swing` added to `shared/jvmMain`** (2026-06-13) — `Dispatchers.Main` now resolvable in `jvmTest` and `DesktopFloatingWindowController`; still present in `desktopApp` (harmless duplicate).
- [x] **Coil network fetcher added** (`coil-network-ktor3`) — remote album art loads;
      `RedefineNCMApp` registers `KtorNetworkFetcherFactory`.
- [x] **`Platform` form-factor flags reconciled** — `isDesktop`/`isMobile` have common defaults;
      all enabled targets, including Web, compile against the same interface.

### Misplaced / misnamed
- [x] **`MediaControlsIntegrator` moved to commonMain** (2026-06-11) — now in
      `commonMain/smtc/MediaControls.kt`; the misnamed `jvmMain/.../WindowsSmtcIntegration.kt` was
      deleted and replaced by `jvmMain/smtc/WindowsMediaControls.kt` (the OS binding home).

### Platform integration status
- [x] **App, DI and real players are wired on all enabled targets.** `initKoin()` runs from every
      entry point; Android binds `ExoPlayerPlatformPlayer`, Desktop binds `JvmMediaPlayer`, iOS
      binds `IosAVPlayer`, and Web binds `WebPlatformPlayer`. Android, Desktop and Web are
      build-verified in this repository; iOS source remains macOS/Xcode-gated.
- [x] **Song recognition is wired on all enabled targets** (2026-07-12). Shared fingerprint,
      resampler, DTO and ViewModel tests pass in `:shared:jvmTest`; Desktop compilation, Android
      assembly, and Wasm compilation/browser tests/distribution are green. A recorded audio sample
      generated by the Kotlin fingerprint path matched through the live LAN `/audio/match` backend.
      **Verification boundary:** these checks do not exercise live microphone capture on real
      Android, iOS, Desktop, or Web devices. iOS remains source-only here because Windows has no
      Xcode.
- [x] **Playback record reporting + account readback — IMPLEMENTED; START ROUTE NOT LIVE-VERIFIED** (2026-07-13).
      The configured backend was upgraded to `4.36.2`; route probes confirm `/scrobble/v1` and
      `/relay/play/state/submit` are registered. Upstream source implements `/weblog`; stable
      playback now sends its dedicated `startplay` action immediately, while half-play accounting
      uses `/scrobble/v1` exclusively. The same startplay request shape previously succeeded as the
      first stage of a controlled two-stage old-backend check, but was not isolated there.
      Structured result state, bounded account readback, credential/generation isolation, SQLDelight
      level-cache refresh, and current-account UI refresh are covered by common/JVM tests. A direct
      real-account check of the combined startplay + NCBL behavior on the upgraded backend is still pending.
- [ ] **Precise inbound playback roaming remains externally gated** (2026-07-13). The deployed relay
      route supports submit, but no pull/get contract is available, and record/recent endpoints omit
      progress, session ID, play mode, revision, and remote queue state. Exact two-way progress/queue
      restoration needs a new server API. Windows cannot validate the iOS runtime path.
- [x] **Android audio backend — ExoPlayer + MediaSession, BUILD-VERIFIED** (2026-06-13).
      `media3-exoplayer 1.10.1` + `media3-session` added to `androidMain` (and to `:androidApp`
      directly for `PlaybackService`).
      - `shared/src/androidMain/player/RedirectingDataSourceFactory` intercepts
        `redefinencm://playbackPlaceHolder?id=xxx` URIs and resolves to real CDN URLs via
        `Repository.getSongUrl()` (runBlocking on ExoPlayer's IO thread — same as original).
      - `shared/src/androidMain/player/ExoPlayerPlatformPlayer` is a Koin singleton (created on
        the main thread in `androidContext()`); implements all `PlatformPlayer` StateFlows via
        `Player.Listener`; position synced every 200 ms while playing.
      - `AndroidPlatformModule.platformModule()` now overrides the shared `InMemoryPlatformPlayer`
        binding with `ExoPlayerPlatformPlayer`.
      - `androidApp/PlaybackService` is a `MediaSessionService`; wraps the same Koin-singleton
        ExoPlayer in a `MediaSession` for OS media controls; registered in `AndroidManifest.xml`
        with `foregroundServiceType="mediaPlayback"` + required foreground-service permissions.
      - Lyric sync added to `NowPlayingViewModel.initLyricSync()` — combines `currentPosition` +
        `lyricMap` → `lyricIndex` + calls `LyricNotificationController.updateLyric(...)`.
      - **Build-verified:** `:shared:compileAndroidMain`, `:androidApp:compileDebugKotlin`,
        `:androidApp:assembleDebug` (APK), `:shared:compileKotlinJvm`, `:shared:jvmTest` all green.
- [x] **Desktop native media controls are implemented.** Windows SMTC uses direct JNA/COM WinRT
      interop without a helper DLL; Linux uses MPRIS. Both consume the shared metadata and player
      command pipeline. Windows has a real-HWND native regression test; JNA `Structure` types used
      across the module boundary must not be private because JDK 21 blocks reflective field access.
      Keep all Windows session creation, metadata/timeline updates, and release on the dedicated
      SMTC MTA thread so `RoInitialize` and `RoUninitialize` stay paired on one thread.
- [x] **iOS Live Activity source and Xcode target are wired.** Kotlin publishes serial
      `LiveActivityData`; Swift drives ActivityKit and the LyricWidget extension renders Lock Screen
      and Dynamic Island content. Runtime verification still requires macOS and a real iOS build.
- [x] **Desktop floating window is wired and Expressive** (updated 2026-07-12) —
      `desktopApp/main.kt` opens a frameless / translucent Compose window, derives its palette
      from the visible artwork, exposes transport controls and a user-toggleable always-on-top
      state, and renders `DesktopFloatingWindowController`'s `floatingLyricData`.
- [x] **The shared UI uses real Material 3 Expressive** (updated 2026-07-12) —
      `MaterialExpressiveTheme`, `MotionScheme.expressive()`, the complete typography scale,
      shared page/state/motion primitives, and visible-image-driven local palettes are applied
      across the KMP pages. Palette foreground roles are contrast-checked; Android uses Palette
      while JVM/iOS/Web share the RGB555 quantizer.

### Not started
- [x] **SQLDelight cache** (D3) — DONE (plugin + 10 tables + drivers + cache-then-network +
      PlayerStatus queue persistence; build-verified 2026-07-13).
- [x] **`PlayQueue` + `PlayQueueTest`** (shuffle regression suite) — DONE (2026-06-11), pure
      Kotlin and build-verified. Android, Desktop and Web publish queue/index/current media from a
      single rebuilt play-order snapshot; do not reintroduce parallel ordering paths.
- [ ] Domain model layer (DTO → domain mapping), if desired.
- [ ] Voyager navigation wiring (confirm whether Voyager is the chosen nav or remove it).
- [ ] Goal #4 verified dependency upgrade + cross-repo version convergence.
- [x] CI pipeline covers common tests, Android, all three Desktop packages, iOS compile/Xcode
      checks, Web browser tests + production distribution, artifacts, tag releases and aggregate
      Telegram status.

> **When in doubt, the source on disk is the truth about _what exists_; this document is the
> truth about _what it should become_.** If they conflict on a fact (e.g., a version number),
> trust the file and fix this doc; if they conflict on a decision (e.g., source-set naming),
> trust this doc and fix the code.
