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

- **直接提交并推送到 `main`。** 常规改动不要新建分支、不要开 PR——本仓库的既定流程就是所有提交
  直接落在 `main` 上。这条**优先于**"在默认分支上要先开分支"的通用助手习惯：在这个仓库里不适用。
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
5. **Aggregate multiple music providers** (NetEase first, QQ Music second) — see
   [D6](#d6--multiple-music-providers-keyed-by-provider--planned-recorded-2026-08-20).
   Start with the provider-neutral domain model; it is worth doing before any second provider
   exists.

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
fully wired: plugin + 11 `.sq` tables under `shared/src/commonMain/sqldelight/` (user detail /
user level / user playlist / playlist detail / playlist tracks / recommend ×2 / lyric / comment /
**PlayerStatus** / **DownloadQueue**) + `DatabaseDriverFactory` expect/actuals
(android/native/sqlite/browser storage). `Repository`
implements cache-then-network for all cached endpoints and persists/restores the play queue
via the `PlayerStatus` table. Schema version 4 includes formal `1.sqm`, `2.sqm`, and `3.sqm` migrations
for the playlist detail/tracks, comment, player-status, user-level, and download-queue tables; platform drivers must use
`AppDatabase.Schema` migrations rather than ad-hoc `onOpen` table creation.

The Home/user-playlist profile hero follows the same cache-then-network contract: cached user
detail and level data render immediately in the status surface below the avatar, remain visible
while the network refresh runs, and are replaced only by a valid network response.

### D4 — The lyric surface is a capability interface; the transport surface is not shared — **(updated 2026-09-08)**

The two surfaces are wired differently, and the old wording here described both as "one common
contract, four platform actuals". That was true of neither.

**Lyric display — `notification/LyricSurface`.** `commonMain` owns three interfaces, and a
target implements the one matching what it can do:

- `LyricSurface` — `updateLyric(...)`, `clearFocus()`, `reset()`. Every target.
- `OptionalLyricSurface` — adds `settingLabel` and `setEnabled()`, for a surface Settings can
  switch off. Android's extra notification and the desktop window.
- `WindowedLyricSurface` — adds `setLocked()` and `setAlignment()`, for a surface that is a
  window of its own. Desktop only.

`expect val lyricSurface: LyricSurface` is the entry point; Settings tests it with `as?`. This
replaced an `expect object` carrying every capability, which forced three targets to declare
stub members for what they cannot do. **Do not add a capability to `LyricSurface` that only one
target has** — that is how the stubs came back last time. Add an interface.

Each target's own extras stay off the interfaces and on its object: `DesktopLyricWindow` owns
the window's visibility, lock and alignment flows, `AndroidLyricNotification` owns `init()`, and
`IosLiveActivity` owns `liveActivityData` and its observer.

**Transport controls — not one contract.** Android, iOS and the browser drive the OS transport
from inside their own players, because that is where the playback state is first-hand: ExoPlayer
keeps its `MediaSession`, `IosAVPlayer` writes `MPNowPlayingInfoCenter` and installs
`MPRemoteCommandCenter` targets, and `WebPlatformPlayer` writes the Media Session and installs
its action handlers. Only the desktop needs a channel, because its JVM player has no OS
transport: `jvmMain/smtc/MediaControlsIntegrator` holds `MediaControlMetadata` and
`DesktopMediaControls` picks the host's backend to observe it.

`commonMain/smtc/MediaControlsSink` and `expect val osMediaControls` express that: the desktop's
is the integrator, and `nonDesktopMain`'s does nothing. The view model calls it unconditionally
and only the desktop acts on it.

| Platform | Lyric surface | Transport surface |
|---|---|---|
| Android | `AndroidLyricNotification` — live-update `Notification` on a channel (`OptionalLyricSurface`) | Media3 `MediaSession`, inside `ExoPlayerPlatformPlayer` |
| iOS | `IosLiveActivity` — ActivityKit Live Activity → Dynamic Island + Lock Screen (`LyricSurface`) | `MPNowPlayingInfoCenter` / `MPRemoteCommandCenter`, inside `IosAVPlayer` |
| Desktop (JVM) | `DesktopLyricWindow` — frameless, always-on-top Compose window (`WindowedLyricSurface`) | Windows SMTC (JNA/COM), MPRIS (D-Bus), macOS now-playing — all observing `MediaControlsIntegrator` |
| Web (WASM) | `WebLyricSurface` — in-page lyric element (`LyricSurface`) | Browser Media Session metadata/actions, inside `WebPlatformPlayer` |

**iOS bridge pattern (updated decision, 2026-07-10):** Kotlin does **not** call ActivityKit
directly. `IosLiveActivity` exposes `LiveActivityData` to the Swift main-app bridge, which
serially starts/updates/ends the ActivityKit activity. The `LyricWidget` extension renders the
resulting `ContentState`. Its `LyricActivityAttributes` lives in `iosApp/LiveActivityShared/`,
a synchronized folder in **both** Xcode targets — the app and the extension must agree on
`ContentState` field for field, and it used to be hand-mirrored in two files. Text does not
require an App Group. An App Group is only needed if artwork is later shared with the extension
through a file cache.

### D5 — Full-screen AMLL is one route with one native Compose renderer — **DONE (updated 2026-09-06)**

`AmllPlayerScreen` is the only full-screen navigation route and it renders `NativeAmllScreen` on
every target. **There is no WebView anywhere in this project.** The Legacy AMLL WebView renderer
— Android System WebView, Windows x64 WebView2 (`WebviewJna`), iOS `WKWebView` — the
`amllAssets/amll/player.html` bundle it drove, the `AmllBridge.*` argument encoding, the
`androidApp/amll-builder/` Node builder, the `useNativeAmllRenderer` setting and its backup
field were all removed on 2026-09-06. Do not reintroduce a WebView, an HTML lyric host, a
renderer preference, or a second *lyric* destination. The pre-migration `FullLyricScreen` and
`NowPlayingScreen` stay removed; the Now Playing entry page below is a new surface, not their
return.

The lyric engine lives in a **separate repository**,
[`RedSTARO/AMLL_Jetpack_Compose`](https://github.com/RedSTARO/AMLL_Jetpack_Compose), vendored as
the git submodule `AMLL_Jetpack_Compose/` and consumed as a Gradle composite build:
`settings.gradle.kts` calls `includeBuild("AMLL_Jetpack_Compose")` and `:shared` depends on the
`com.leejlredstar.amll:amll-compose` coordinate, which Gradle substitutes with the local project.
A fresh clone needs `git submodule update --init`; the settings script fails with that hint when
the directory is empty. Changes to the engine are made *inside* the submodule, committed and
pushed there, and then the new submodule pointer is committed here — never edit the engine
through this repository's history.
`TYPESAFE_PROJECT_ACCESSORS` does not generate accessors for included builds, so that coordinate
— not a `projects.*` accessor — is the dependency notation. Kotlin, Compose Multiplatform, AGP
and the target set must stay identical in both catalogs: an included build only substitutes when
both sides publish the same Kotlin target variants.

The library owns the lyric model and the renderer: LRC/YRC/TTML parsing (`LyricParser`,
`TtmlLyricParser`), AMLL's optimizer and grouping, the spring/timeline/layout engine, the
word-level mask/emphasis/ruby renderer, platform word and grapheme segmentation, the
reduced-motion system read, the browser DOM event bridge, and the artwork background stack.
This repository owns everything app-shaped: `NativeAmllScreen` as the composition root,
`NowPlayingViewModel` wiring, the song-details surface, the control island, and
`NativeDynamicCoverLayer`. Two host couplings are deliberately inverted — `AmllBackground` takes
an `AmllDynamicCoverLayer` slot instead of importing the app's video leaf, and the viewport uses
the library's own `amllComposeWheelDeltaMode` expect/actual instead of the app's `getPlatform()`.
Do not re-couple them.

The renderer consumes media-ID-scoped `NowPlayingViewModel` state, lyric source policy, favorite
state, and the song-details request contract. Late state and song-details results must stay
scoped to the current media ID.

The playback control island (`AutoHideMiniPlayerController`) is one common Compose surface on all
four targets, and the Android presentation is the baseline: the same 3.6-second auto-hide on
every platform. Its one desktop-only element is the output volume row of the expanded island,
a slider dedicated to volume between the playback card and the action pill: phones leave
volume to the hardware keys and the system panel, while the full-screen lyric page covers the
desktop main window's volume strip. Clicking a lyric line seeks and nothing else; the island
expands on a click or tap between lines, on keyboard input, and from its own collapsed bar.
Desktop's former 30-second timeout belonged to the in-page `#desktop-console`,
which existed only because Compose could not draw above the WebView2 child HWND; it went with
the page. `DesktopOverlayWindow` was that same workaround and is removed — desktop uses normal
Compose transitions, the in-scene snackbar host, and `ModalWideNavigationRail`.

Dynamic video is the only intentionally narrow platform leaf:
`NativeDynamicCoverLayer` is an expect/actual composable inside the shared artwork slot.
Android uses Media3 with a `TextureView`, iOS uses `AVPlayerLayer` inside `UIKitView`,
Desktop/JVM decodes FFmpeg frames into Compose `ImageBitmap`, and Web/WASM hosts an
`HTMLVideoElement` beneath the CanvasKit scene. Dynamic-cover capability must never decide
renderer availability, renderer selection, Desktop overlay ownership, or navigation routing.

### D6 — Multiple music providers, keyed by provider — **PLANNED (recorded 2026-08-20)**

The app aggregates more than one music service. NetEase Cloud Music is the first; QQ Music is
the second. The decisions below are settled; the work is not started.

**Identity.** A track, playlist, album or user is identified by a `(provider, rawId)` pair,
serialized as `"ncm:123456"`. `MediaInfo.id` is already a `String`, so the player and the
`placeholderUri` → `StreamUrlResolver` indirection carry composite ids unchanged — the player
never sees a real stream URL and must not learn what a provider is. Roughly fifty call sites
currently assume a numeric id (`toLongOrNull()`, `songId: Long`); those are the migration
surface, not the design.

**Credentials are keyed by provider, not by account.** One signed-in account per provider.
`SettingKeys.COOKIE` / `SettingKeys.SERVER` become per-provider entries. Multiple accounts of
the *same* provider are explicitly out of scope; do not add an account dimension to the
credential store to "future-proof" it.

**Both library views ship, and the choice is a setting.** A unified library that merges every
provider, and per-provider tabs. Neither is the default-and-only view. This means the domain
model must carry provider identity all the way to the UI, because rows in the merged view have
to say where they came from.

**The UI must not bind to a provider's DTOs.** Today eight files under `ui/` and `viewmodel/`
import `data.api.dto` directly and read NetEase-shaped fields such as `song.al.picUrl`. A
provider-neutral domain model (`Track`, `Playlist`, `Album`, `UserProfile`) is mapped at the
`Repository` boundary, and provider clients sit behind one interface. This is worth doing on its
own merits and does not depend on QQ Music shipping.

**Cached tables are keyed by provider.** Every `song_id INTEGER` cache table gains a provider
column and a composite key over a text id; two providers' id spaces would otherwise collide
silently. Existing rows migrate to `provider = 'ncm'`. This and the credential move are the only
steps in the plan that can destroy real user data, and they carry over a year of it.

**Three existing contracts become provider-conditional and must not be quietly broken:**

- The shuffle / queue ordering invariant below still holds for a queue mixing providers.
- The lyric source-mode privacy gate is currently a closed policy over two sources, where
  `BACKEND_ONLY` must never *read or request* the other. Extending it to N providers is a
  redesign of that policy, not a new enum entry.
- The playback reporting contract is NetEase-specific. It must not fire for another provider's
  tracks.

**Backend.** QQ Music has no public API, so it needs a self-hosted service, the same shape as
the NeteaseCloudMusicApi server this app already points at. Surveyed 2026-08-20:

| Project | Stars | Last push | Shape | License |
| --- | --- | --- | --- | --- |
| `Rain120/qq-music-api` | 1046 | 2026-08 | Koa2 HTTP server (TS) | MIT |
| `jsososo/QQMusicApi` | 1616 | 2024-06 | Express HTTP server | GPL-3.0 |
| `L-1124/QQMusicApi` | 451 | 2026-08 | Python library | GPL-3.0 |

`Rain120/qq-music-api` is the working pick: it is the closest analogue to the server already in
use, it is still maintained, and it is MIT. `jsososo/QQMusicApi` has the most stars but has not
been pushed to in over two years with 64 issues open. `L-1124/QQMusicApi` is healthy but is a
library rather than a service, so it would need a server written around it.

The backend runs as a separate process reached over HTTP, so its GPL does not reach this
AGPL-3.0-only app either way; MIT simply avoids the question.

Two limits are properties of the services, not of this design: these APIs are unofficial and
break without notice, and paid tracks cannot be streamed without an entitled account. Neither is
a reason to change the plan; both are reasons not to promise coverage.

**The backend is deployed locally and its coverage is measured (2026-08-27).** `Rain120/qq-music-api`
runs in WSL (Debian 13, Node 20.19.2) on port 3200, reachable from Windows at `localhost:3200`.
Two setup traps: `npm` on the WSL `PATH` resolves to the *Windows* binary through interop and
must not be used to install, and corepack's current `npm` 12 and `pnpm` 11 both refuse Node 20 —
`corepack prepare pnpm@9.15.9 --activate` is the combination that installs. WSL also tears the
server down when the last session closes, so it needs a session held open rather than `nohup`.
An Android device reaches it over `adb reverse tcp:3200 tcp:3200`; WSL is in NAT mode, so a LAN
device would otherwise need a `netsh portproxy` rule.

**`localhost` is not reachable from the JVM in this setup.** WSL's port forwarding answers on the
IPv6 loopback only: `http://[::1]:3200` and the WSL address both return 200, while `127.0.0.1`
is refused. Windows `curl` hides this because it tries IPv6 first; a JVM client resolves
`localhost` to IPv4 and gets `Connection refused`. So the desktop app needs `http://[::1]:3200`
in the QQ backend field, or a `netsh interface portproxy` rule to make the IPv4 loopback work.
This is a property of the WSL deployment, not of the client — the same client reaches a natively
hosted backend through `localhost` unchanged.

Measured against the live server, signed out:

| Capability | Endpoint | Anonymous result |
| --- | --- | --- |
| Search | `/getSearchByKey?key=` | works |
| Playlist browse | `/getSongLists` | works |
| Playlist detail | `/getSongListDetail?disstid=` | works, full track metadata |
| Lyrics | `/getLyric?songmid=` | works — LRC, plus a separate `trans` field |
| Stream URL | `/getMusicPlay?songmid=&quality=` | free tracks at `m4a`/`128` only |

This sharpens the limit stated above. The gate is account entitlement checked server-side at
`vkey.GetVkeyServer`, not client-side DRM, so signed out a *free* track does return a real URL —
verified by fetching one, which answered `206 audio/mpeg` with an `ID3` header and honoured range
requests, so seeking works. The same call for a VIP track returns an empty `purl` at every
quality, and `320`/`flac` are empty even for free tracks. A cookie is therefore needed for parity
with the NetEase side, not merely for personal playlists. What a cookie actually unlocks has not
been measured, and a non-VIP account is not expected to lift the VIP wall.

**The cookie is held by the app, and this backend cannot yet receive it.** The app stores a QQ
cookie beside the NetEase one and sends it as a `Cookie` header on every QQ request. That is the
right architecture rather than installing a credential into the backend: it lets one backend serve
several clients, and it is the only way the Android build can sign in at all, since it has no
access to the backend's config file.

`Rain120/qq-music-api` ignores that header, for four independent reasons: `/user/setCookie`
answers `403` by design, the cookie middleware reads only the config file, the outbound request
never forwards a `Cookie` header upstream (the credential only derives `uin` into query params),
and `export const userInfo = appConfig.user` captures a reference while `updateConfig()` replaces
the object — so merely re-enabling `setCookie` would log success and change nothing. Until the
backend is patched, QQ requests are anonymous whatever the app has stored, and the only working
credential path is its `config/user-info` file, which is desktop-only. The patch, its staleness
trap, and its caveats are written up in
[docs/qq-music-api-per-request-cookie.md](docs/qq-music-api-per-request-cookie.md); it is
deliberately not applied, because it forks a third-party repository.

Two shape hazards for the mapper. Routes declare path parameters but every controller reads
`ctx.query`, so `/getSearchByKey/周杰伦` answers `400 search key is null` — pass query strings.
And the two list shapes disagree: search rows carry `songmid`/`songname`, playlist rows carry
`mid`/`name` with nested `singer[]` and `album{}`. The provider client normalises both.
`getMusicPlay` also sends a hardcoded `sign` constant, which is the most likely thing to break
when QQ rotates it.

**Order of work.** The neutral domain model comes first — it is the keystone, it is useful with
or without QQ Music, and it removes an existing coupling. Provider abstraction follows, then the
schema and credential migrations, then aggregation UX, then the QQ Music client itself.

**Shipped so far (2026-08-27).** `ProviderItemId`, `MusicProvider` and `MusicProviderRegistry`,
a `NeteaseProvider` adapter over the untouched `Repository`, a `QQProvider`, provider dispatch in
all four platform stream resolvers, provider-neutral search end to end, and the settings to turn
QQ Music on, point it at a backend, sign in with a cookie, and choose merged or per-provider
grouping.

Credentials stay additive: `qqEnabled` / `qqServer` / `qqCookie` sit beside the existing
`cookie` / `server` keys rather than renaming them, so nothing migrates and no user data is at
risk. `qqCookie` is excluded from the settings backup for the same reason `cookie` is — a shared
export must never carry a credential — while `qqServer` and the aggregation choice travel with a
backup the way `server` does.

`MediaInfo.id` keeps NetEase ids bare and prefixes only foreign providers. Around fifteen call
sites read `MediaInfo.id.toLongOrNull()` to reach NetEase-only features — lyrics, the local
download lookup, the song wiki, the download-status chip — and prefixing NetEase's own ids would
switch all of them off at once. A bare id still parses back to NetEase, so dispatch is unaffected,
while a `qq:` id is correctly read by those sites as "not a NetEase song".

**Still open, in the order they matter.** QQ results are uncached, because the provider column on
the eleven cache tables is the data-destroying step and belongs in its own change. The library
and playlist screens still bind to NetEase DTOs, so the aggregation setting currently only reaches
search — the per-provider tabs the setting promises are not built. QQ tracks cannot be downloaded,
since the download queue is keyed by a numeric song id. And the lyric source-mode privacy gate is
still a closed policy over two NetEase-era sources: QQ lyrics are reachable through the provider
interface but are not wired into that pipeline, which is a redesign of the policy rather than a
new enum entry.

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
tag format is `v<major>.<minor>.<patch>`; the current base tag is `v0.0.2`. Full app version
names are `baseTag.shortCommitHash`, e.g. `v0.0.2.cf6cea74`. Build numbers are
`git rev-list --count HEAD`.

The full `tag.hash` value is the **only product version**. Every runtime and every CI artifact
uses it. Native installer fields that reject letters or additional components are numeric
transport metadata derived from the same Git revision; they must never be presented as a second
product version.

Platform mapping:

- Android: `versionName = full app version`, `versionCode = commit count`.
- Shared runtime (Android/iOS/Desktop/Web): generated common `BuildInfo.VERSION_NAME` is the full
  product version. It also exposes the tag components, build number, and native package adapter;
  the standard Web distribution additionally carries the same data in its generated `version.json`.
- Desktop DMG/MSI/DEB: CI artifact filenames use the full product version. Their internal
  `packageVersion` / `msiPackageVersion` use
  `<base major + 1>.<base minor>.<commit count + base patch>`. The positive major fixes the macOS
  `CFBundleVersion` constraint and reserves package major 1 above the historical `1.0.0` installer;
  the stable Windows `upgradeUuid` remains unchanged.
- iOS app + LyricWidget: `CFBundleShortVersionString = base semantic version`,
  `CFBundleVersion = commit count`, and `RedefineNCMVersionName = full product version`.
  `iosApp/Scripts/stamp-version.sh` **must run before `xcodebuild`** — it writes
  `iosApp/Configuration/Version.xcconfig` (git-ignored), which `Config.xcconfig` pulls in with
  `#include?`, and `ProcessInfoPlistFile` substitutes those settings into both Info.plists. Do
  **not** turn this back into a build phase that rewrites the built plist: the app target has an
  AppIcon asset catalog, so actool emits a partial plist and Xcode schedules `ProcessInfoPlistFile`
  after every build phase — the script would never see the file, and declaring the plist as a phase
  input produces a dependency cycle. CI passes the same version inputs resolved once by
  Gradle, and the script fails if the supplied full version differs from `tag.hash`.
- CI resolves the version once in the test job, passes the same tag/hash/build inputs to every
  platform job, and names APK/DEB/MSI/DMG/Web artifacts with the full product version.

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
│       ├── skiaMain/                    # JVM + iOS + Web: anything needing only a Skia bitmap
│       ├── nonDesktopMain/              # Android + iOS + Web: no app-owned window, no route picker
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
│       │   │   └── LyricNotificationController.kt   # LyricSurface interfaces + expect val
│       │   ├── recognition/
│       │   │   ├── AudioFingerprint.kt   # Pure-Kotlin fingerprint extractor + codec
│       │   │   ├── MicrophoneRecorder.kt # common capture contract
│       │   │   └── PcmResampler.kt       # platform PCM → 8 kHz mono
│       │   ├── smtc/
│       │   │   └── (MediaControlsIntegrator)        # ⚠ currently in jvmMain, not commonMain — see divergence
│       │   ├── util/
│       │   │   ├── LyricParser.kt        # Pure-Kotlin LRC parser
│       │   │   ├── CoilImageTheme.kt      # expect fun themeColorFromCoilImage — album-art accent
│       │   │   │                          #   (one skiaMain actual covers JVM/iOS/Web)
│       │   │   └── Settings.kt           # expect class PlatformSettings + SettingKeys; the
│       │   │                             #   suspending getters are commonMain extensions
│       │   ├── viewmodel/
│       │   │   ├── LoginViewModel.kt
│       │   │   ├── MainViewModel.kt
│       │   │   ├── NowPlayingViewModel.kt # holds shuffle invariant (rebuildPlaylistFromTimeline)
│       │   │   └── SongRecognitionViewModel.kt
│       │   └── ui/
│       │       ├── amll/NativeAmllScreen.kt    # the only full-player renderer, all four targets
│       │       ├── component/AutoHideMiniPlayerController.kt # the playback control island
│       │       ├── component/NativeDynamicCoverLayer.kt # narrow platform video leaf
│       │       ├── component/SongWikiDetails.kt  # shared responsive song-details surface
│       │       ├── screen/SongRecognitionScreen.kt
│       │       ├── component/Expressive.kt      # connected-list shapes
│       │       └── theme/{Color,Type,Theme}.kt  # real M3 Expressive theme + image-derived palettes
│       ├── androidMain/  …/Platform.android.kt, notification/AndroidNotificationController.kt,
│       │                  di/AndroidPlatformModule.kt, util/AndroidSettings.kt,
│       │                  recognition/AndroidMicrophoneRecorder.kt,
│       │                  ui/component/NativeDynamicCoverLayer.android.kt (Media3 video leaf),
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
├── AMLL_Jetpack_Compose/   # git submodule — the AMLL lyric engine + renderer (own Gradle build, includeBuild)
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

## Logged-in startup actions

On a process start that resolves an already logged-in account, the app calls
`/vip/growthpoint/getall` once to claim all completed VIP task rewards. This action uses the
startup credential snapshot, runs independently from account-page loading, is not repeated by
account-data retries or an in-process login, and is cancelled if the account is cleared or switched.

---

## How playback & lyrics work (ported contract)

1. A queue is a `List<MediaInfo>` whose `placeholderUri` is
   `redefinencm://playbackPlaceHolder?id=<songId>` — **not** a real audio URL.
2. Each platform's `PlatformPlayer` resolves the placeholder to a real CDN stream URL at play
   time via `NCMApi.songUrlV1()` (a `StreamUrlResolver`). Stream URLs are **never persisted**
   — they expire and are fetched fresh per session.
3. `NowPlayingViewModel` mirrors `PlatformPlayer`'s `StateFlow`s (state, position, duration,
   currentMedia, queue, currentIndex, shuffleEnabled) to the UI.
4. Lyric pipeline: `PlatformPlayer.currentMedia` → `LyricResolver` applies the persisted source
   policy → common timed lines → `PlatformPlayer.position` matches the current line → UI scroll **and**
   `lyricSurface.updateLyric(...)` (notification / Dynamic Island / floating
   window).

### Lyric source contract

The persisted source policy has four stable values: AMLL TTML first with backend fallback
(default), backend first with TTML fallback, TTML only, and backend only. `TTML_ONLY` and
`BACKEND_ONLY` must never read or request the other source. A source counts as found only when it
parses to a non-empty primary timed line list; translation/romanization alone is not a hit. Missing
settings use the documented TTML-first default, while an unrecognized persisted wire value fails
closed to backend-only.

AMLL TTML lookup uses the current positive NCM song ID: exact
`amll-ttml-db.stevexmh.net/ncm/<id>` first, then the Bikonoo exact-ID search result with an
NCM-ID membership check. Do not automatically apply fuzzy title matches. The Bikonoo search API is
unversioned, so failure or schema drift must fall through according to the selected policy.

Public AMLL DB traffic uses a dedicated Ktor client with no NCM Cookie, `realIP`, timestamp or NCM
base URL. Do not reuse the authenticated NCM `HttpClient`. Lookup is bounded so a third-party
timeout cannot indefinitely delay the backend fallback: response bodies are byte-bounded, each
network hop has a timeout, and the complete lookup has a 12-second ceiling. The backend provider
keeps the pre-existing four-attempt retry only when its cache/network flow emits no value; an
explicit no-lyric response is not retried. Track changes and source changes cancel the previous
work; writes back to `NowPlayingViewModel` are scoped to both media ID and request generation.

`CachedLyric.json` is a versioned bundle that can hold the NCM backend DTO and AMLL TTML
independently while lazily reading the previous raw-`Lyric` JSON shape. Keep those two entries
independent when changing cache code. A valid external TTML entry is fresh for 24 hours; an expired
entry remains displayable while a bounded refresh runs. Every target feeds the original TTML through
the common `TtmlLyricParser`, whose event parser and AMLL conversion preserve the behavior of
`@applemusic-like-lyrics/ttml` 1.0.1 for the shared Compose page, notifications, and other surfaces.

### Downloaded media sidecar contract

Downloaded audio remains `<songId>.<extension>`. Lyrics use multi-segment sidecars beside it so
no platform audio scanner can mistake them for songs:

- AMLL source: `<id>.lyric.ttml` stores the upstream TTML verbatim.
- Backend source: YRC is `<id>.lyric.yrc`; line LRC is `<id>.lyric.lrc`, or
  `<id>.lyric.line.lrc` when YRC is the primary format; translations and romanizations are
  `<id>.lyric.translation.lrc` and `<id>.lyric.romanization.lrc`.
- App-downloaded artwork is `<id>.cover.<detected-extension>` and keeps the validated upstream
  bytes. Android stores artwork under app-specific external-files storage behind a restricted
  `FileProvider` URI, with an internal-files fallback and `.nomedia` marker; it must never publish
  artwork as an `image/*` row in shared `MediaStore` or place it beside public audio. Inspection
  and local playback migrate and remove historical public Android cover sidecars. Other targets
  retain the adjacent-sidecar layout.

Derived TTML line text must never be persisted as if it were an original backend LRC. Local
playback applies the same four-state source policy as network playback: within each selected
source, a valid local sidecar is preferred only when the song audio is locally present; an
opposite-source sidecar cannot bypass `TTML_ONLY` or `BACKEND_ONLY`. A damaged or unparseable
sidecar falls through to the selected online provider.

Audio publication is the download success boundary. As soon as audio is published it enters
`DownloadedSongsCache`; lyric and artwork saves run independently and may fail without rolling
back or marking the audio failed. Completed/download-imported rows expose separate lyric and
artwork backfill actions. Queue status is reconciled against actual sidecars rather than the
SQLDelight lyric cache. Deleting a downloaded song also deletes its sidecars.

Platform storage uses Android MediaStore `content:` URIs for public audio/lyrics and restricted
app `FileProvider` `content:` URIs for artwork, JVM/iOS `file:` URIs, and Web OPFS keys resolved
to temporary `blob:` URLs. Runtime URIs are never persisted in `PlayerStatus` or the download
queue, and Web blob URLs must be revoked when no longer active.
Native Compose surfaces consume only app-managed, path-validated artwork URIs returned by
`LocalMediaAssets`; platform image loaders must not broaden that contract to arbitrary `content:`
or cross-directory `file:` paths.
Pending/backup files stay hidden and are excluded from scans. Web audio discovery accepts only
the single-extension audio shape and explicitly excludes `.lyric.*` and `.cover.*`.

Playback has two stacked full-screen surfaces and one lyric renderer. `MiniNowPlayingBar`, the
Desktop rail's now-playing action, and OS/deep-link now-playing requests open
`PushedDest.NowPlaying` → `ui/screen/NowPlayingScreen.kt`: the Apple-Music-shaped entry page
(artwork, title, wavy seek track, transport, floating toolbar). Its toolbar action button —
the English quotation marks, `AppIcons.FormatQuote` — pushes `PushedDest.FullLyric` →
`AmllPlayerScreen` → `NativeAmllScreen`, which renders lyrics on every platform. Back from the
lyric page returns to Now Playing; back from Now Playing returns to the app. Both surfaces are
`focusOrPush` destinations, hide the mini player, rise as one sheet over the tabs and cross-fade
between each other (`isPlayerSurface` in `App.kt`). There is no renderer preference to resolve.
Do not add lyric rendering to the Now Playing page, and do not route any entry point straight
to the lyric page.

The renderer exposes the top-right song-details affordance and requests `/song/wiki/summary`
through `Repository` for the current song. They render only the
`SONG_PLAY_ABOUT_TAB_SONG_BASIC` display fields; recommendation and playlist blocks are
excluded. Requests and rendered state are media-ID scoped, and changing tracks must close the
surface and prevent a late response from replacing the new track's state.

Now-playing favorite controls query `/song/like/check` with the current song ID only after the
current account UID is available. A successful response containing that ID renders the filled
heart/accent state; an absent ID, logged-out state, failed check, track change, or account change
renders the outlined state. A successful existing `/like` action promotes the same current-song
state to liked immediately. Late checks and actions must be scoped to both media ID and account UID.

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
recent-play or half-play reporting or create an unbounded telemetry queue. Structured relay results remain available
to coordinator diagnostics but are not rendered on the Home/user-playlist page;
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
`shared/src`. The table records the current declarations; claims that any version is still the
latest require the live verification pass described under Goal #4.

| Area | Library (version) | Where |
|---|---|---|
| HTTP | Ktor `3.5.0`: client-core, content-negotiation, logging, serialization-kotlinx-json | commonMain |
| HTTP engines | okhttp (androidMain + jvmMain), darwin (iosMain), JS (wasmJsMain) | per-platform |
| Serialization | `kotlinx-serialization-json 1.11.0` + `kotlin.plugin.serialization` (@kotlin) | commonMain / plugins |
| XML | xmlutil core `0.91.3` (streaming TTML validation/common fallback parsing) | commonMain |
| Concurrency | `kotlinx-coroutines-core 1.11.0` | commonMain |
| DI | Koin `4.2.1`: koin-core, koin-compose | commonMain |
| Images | Coil `3.5.0`: coil-compose (group `io.coil-kt.coil3`) | commonMain |
| Web runtime | `kotlinx-browser 0.5.0` + Ktor JS engine `3.5.0` | wasmJsMain |
| Desktop native APIs | JNA `5.14.0` + dbus-java `5.2.0` | jvmMain |
| Desktop audio + dynamic video | JavaCV `1.5.13` + FFmpeg `8.0.1-1.5.13` (host classifier only) — decodes playback audio and dynamic-cover frames; output stays `javax.sound.sampled` | jvmMain |
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
3. **Keep shared versions converged across both repos** (Kotlin, coroutines, serialization, AGP
   where compatible). Kotlin `2.4.0` is already converged. AGP parity remains deferred by the
   empirically recorded KMP build failures above; do not force it merely for version equality.
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
  Chroma is raised past baseline M3 on **container** roles only — they pair with dark
  on-colours and can absorb it — while `primary`/`secondary`/`tertiary` hold their tone so
  white-on-colour label contrast survives. `tertiary` is a saturated coral acting as the true
  complement to the teal core.
- **Shape — connected-list language:** large outer corners (~40dp), tight inner corners
  (~4dp), small vertical gaps (~2.5dp) between rows; implemented in
  `ui/component/Expressive.kt` (`connectedListItemShape()`). Interactive elements use pill
  (`RoundedCornerShape(50%)`) or `extraLarge` shapes. The theme shape scale is deliberately
  steep (`extraSmall` 12dp → `extraLarge` 52dp) so panels read as capsules, not cards.
- **Typography:** the scale is bimodal by design. Display and headline roles are
  `Black`/`ExtraBold` with negative tracking; every body role stays `Normal` with positive
  tracking for sustained reading. The **gap** between those groups carries the expressive
  voice, so body sizes must not be scaled up to match the headlines.
- **Shape morphing is the signature interaction.** Material morphs `ButtonGroup`,
  `ToggleButton` and `SplitButton` natively — prefer those over plain buttons/chips.
  `ui/component/ExpressiveShapeMotion.kt` supplies the same behaviour for surfaces the app
  draws itself: a `Morph`-backed `Shape`, named `MaterialShapes` pairs, and a press-driven
  progress bound to the theme motion scheme. `ExpressiveArtwork` takes the enclosing row's
  `interactionSource` to morph a cover while it is held.
- **Progress:** wavy indicators for prominent, full-width progress (downloads, recognition,
  user level, desktop mini strip). Do **not** make thin decorative bars wavy — anything under
  roughly 4dp has no room for wave amplitude and renders as a smudge.
- **Navigation chrome:** narrow windows use a `HorizontalFloatingToolbar` of `ToggleButton`
  items. Wide windows keep the navigation rail — it is the correct responsive pattern for
  desktop and tablet and must not be replaced by a floating toolbar.
- **Use the real Expressive APIs** (`MaterialExpressiveTheme`, motion scheme) provided by the
  pinned `material3` version; custom shapes and page palettes extend that theme rather than
  replacing it.
- **Per-screen:** Now Playing entry page (`NowPlayingScreen`: artwork-derived page gradient,
  `ExpressiveArtwork` press morph that also settles to 86% while paused, Black headline with
  marquee, a `Slider` whose track is `LinearWavyProgressIndicator` with amplitude tied to
  playback, a wide extra-large `FilledIconToggleButton` play/pause with `toggleableShapes()`,
  large tonal skip buttons with `IconButtonDefaults.shapes()`, and a `HorizontalFloatingToolbar`
  whose vibrant FAB is the quotation-mark lyrics button; two columns at ≥840dp landscape);
  Full-screen lyric player (`AmllPlayerScreen` → `NativeAmllScreen`, native Compose
  on every target, with the auto-hiding control island);
  Playlist detail (album-color gradient header, play-all/download-all as one
  `SplitButtonLayout`, connected rows with download indicators); User page (blurred hero +
  avatar, badged playlists);
  Search (pill→bar shared-element transition, suggestion list); Downloads (queue summary hero,
  progress rows, pause/resume/cancel/retry controls); Settings (gradient hero, tonal rows, pill
  actions); Mini-player FAB (image-derived color w/ adaptive content luminance via
  `contrastingTextColor()`, oversized rounded shape).
- **Desktop floating window:** frameless/translucent, blur-behind, current lyric + mini art +
  compact controls, always-on-top toggle (part of `DesktopLyricWindow`).
- **iOS Live Activity:** Lock Screen + Dynamic Island — artwork thumbnail, title, artist,
  current lyric, synced to position.

---

## Build & run

Prereqs: JDK 17, Android SDK (compileSdk 36 / build-tools / minSdk 24), Xcode 16+ (iOS,
macOS only), IntelliJ IDEA / Android Studio with the KMP plugin. Web browser tests require a
locally available Chrome/Chromium-compatible headless browser.

```sh
# First clone only: fetch the AMLL renderer submodule
git submodule update --init
# Android
./gradlew :androidApp:assembleDebug
# Desktop
./gradlew :desktopApp:run
./gradlew :shared:jvmTest
# iOS (macOS only) — then open iosApp/iosApp.xcodeproj in Xcode
./gradlew :shared:iosSimulatorArm64Test
# iOS device build: generate the version xcconfig first, and keep DerivedData off any
# iCloud-synced folder (iCloud stamps com.apple.FinderInfo, which codesign rejects).
sh iosApp/Scripts/stamp-version.sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphoneos -destination 'generic/platform=iOS' \
  -derivedDataPath "$HOME/Library/Developer/Xcode/DerivedData/RedefineNCM_KMP-ios" \
  -allowProvisioningUpdates build
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
  page remains as an internal route (pause/resume/cancel/retry/clear/delete local files). The
  common tab/sidebar entry is removed; Desktop alone exposes the route as a tool in its modal wide
  navigation rail, while Android keeps the `AndroidDownloadService` foreground notification as its
  entry point. The service stores
  the actual downloaded quality from
  `/song/url/v1`'s returned `level`. After publishing audio, it immediately registers the local
  snapshot, then independently saves original-format lyric and artwork sidecars; either sidecar
  may fail without changing the successful audio result. The ordered queue is persisted in
  SQLDelight before Android starts the foreground
  service; interrupted active states restore as queued work on the next process launch. Android
  keeps resumable partial files in app-private storage, validates HTTP byte ranges with strong ETag
  or Last-Modified `If-Range`, and publishes to MediaStore only after the complete file is present;
  a missing validator, changed entity, or server that ignores Range restarts safely from byte zero.
  Local-library sync is snapshot-based, not ID-only: platform scans return
  `DownloadedSongSnapshot` (song ID, file name, URI, size, modified time), `DownloadedSongsCache`
  caches that snapshot for O(1) row indicators, and `SongDownloadManager.syncWithLocalLibrary()`
  reconciles queued tasks with real files while importing disk-only downloads as Completed rows
  with best-effort `/song/detail` metadata. `SongDownloader` actuals only stream files to the
  platform download folder. Android writes `Downloads/RedefineNCM/` through `MediaStore` instead of
  the system `DownloadManager`; JVM writes `~/Music/RedefineNCM/`; iOS streams into
  `Documents/RedefineNCM/` and uses an NSURLSession background transfer for durable downloads.
  Disk-only imports recover sidecar status, Completed rows can backfill lyrics or artwork without
  redownloading audio, and local playback prefers valid same-source lyrics and local artwork.
- **Playlist behaviors**: `replacePlaylist` setting honored on song click,
  `playlistUpdatePlaycount` reported, no auto-jump to the full-screen player (original behavior).
- **Album-art theme color**: `themeColorFromCoilImage()` expect/actual (Android Palette /
  JVM Skia sampling / iOS stub) wired into MiniNowPlayingBar (with luminance-adaptive content
  color + spring animation), PlaylistDetail hero, and both full-screen AMLL renderers.
- **Search shared-element transition** (pill → bar) via `SharedTransitionLayout` in HomeScreen.
- **Responsive nav**: non-Desktop targets use NavigationRail on ≥600dp. Desktop keeps a collapsed
  modal wide navigation rail at every supported window size; expansion overlays content instead of
  resizing it. Full-screen playback remains one `AmllPlayerScreen` destination regardless of
  window size or dynamic-cover capability. No-cookie startup routes to Login.
- **Settings**: server availability check (`/inner/version/`); the legacy-persisted
  `adaptOriginalAndroidLyric` value now controls the optional Android Live Update notification
  and Desktop floating-lyrics window (default off, immediate enable/disable); iOS Live Activity
  and Web lyrics remain independent. There is no AMLL renderer switch: the removed
  `useNativeAmllRenderer` key must not come back, and `SettingsBackupData` deliberately no longer
  declares it — `backupJson` sets `ignoreUnknownKeys`, so older exports still import.
  The lyric-source dropdown persists the four-state TTML/backend policy
  and discloses the third-party ID lookup. A source change applies to the current process
  immediately after the settings write so backend-only cancels an in-flight external lookup;
  durable-write failure rolls the process snapshot and current-song load back. The live-update
  notification uses the lyric as its title.
- **Update check on launch** (checkUpdate setting → GitHub releases/latest → Snackbar).
- Skipped intentionally: `HiddenTestActivity`, `serverMocker` (dev tools), `dailysignin`
  (declared but never called in the original either).

### WebView removal and renderer extraction — DONE (updated 2026-09-06)
- [x] Every WebView is gone: the four `LyricScreen.*` hosts, `WebviewJna`, `AmllWebBridge`,
      `AmllHostState`, `AmllSongDetails`, `AmllRendererSupport.*`, `amllAssets/`,
      `androidApp/amll-builder/`, the `webview-java` dependency and its JitPack repository, the
      ProGuard keep rules, and the `compose.layers.type=COMPONENT` startup workaround.
- [x] `DesktopOverlayWindow` and its `ProvideDesktopOverlayOwner` owner went with it; every call
      site was gated on the Legacy renderer being active.
- [x] The lyric engine and lyric model live in the `AMLL_Jetpack_Compose` submodule
      (github.com/RedSTARO/AMLL_Jetpack_Compose) and are consumed through `includeBuild`.
- [x] `AmllPlayerScreen` renders `NativeAmllScreen` on all four targets. The pre-migration
      `FullLyricScreen` / `NowPlayingScreen` remain removed.
- [x] `ui/screen/NowPlayingScreen.kt` (added 2026-09-06) is the Now Playing entry page every
      player entry point opens; its quotation-mark toolbar action opens the lyric page.
- [x] `USE_NATIVE_AMLL_RENDERER` is dropped from `SettingKeys`, the Settings UI and the backup
      schema; backups written while the setting existed still import.
- [x] The control island is one common surface on all four targets with Android's 3.6-second
      auto-hide everywhere; desktop adds a dedicated output volume slider to the expanded island.
- [x] The desktop lyric window has its own settings: show or hide, lock (no drag or resize, and
      click-through on Windows), and left, centre or right alignment of its two lines.
- [x] This branch passes `:shared:jvmTest`, `:desktopApp:compileKotlin`,
      `:shared:compileAndroidMain`, `:androidApp:assembleDebug`,
      `:shared:compileKotlinWasmJs`, `:shared:wasmJsBrowserTest`, and
      `:shared:wasmJsBrowserDistribution` on Windows. The iOS Kotlin source also passes
      `:shared:compileKotlinIosSimulatorArm64` on Windows; framework linking, the Xcode app,
      simulator tests, and runtime verification remain macOS/Xcode-gated.

### Build catalog — DONE + BUILD-VERIFIED (Android + Desktop + Web + iOS source, updated 2026-07-27)
- [x] `libs.versions.toml` + `shared/build.gradle.kts` declare every dependency the code imports
      (Ktor 3.5.0, kotlinx-serialization-json 1.11.0 + plugin, kotlinx-coroutines-core 1.11.0,
      Koin 4.2.1 + koin-android, Coil 3.5.0 coil-compose, datastore + androidx.core on androidMain,
      `compose.material3` on :desktopApp, and the Web browser dependencies). Engines are scoped
      per source set (OkHttp on Android/JVM, Darwin on iOS, JS/Fetch on Web).
- [x] kotlinx-serialization Gradle plugin applied (root `apply false` + `:shared`).
- [x] Dead `shared/src/desktopMain/` deleted (D1).
- [x] **VERIFIED GREEN:** `:shared:compileKotlinJvm`, `:desktopApp:compileKotlin`,
      `:shared:jvmTest` (PlayQueueTest passes), `:shared:compileAndroidMain`,
      `:androidApp:compileDebugKotlin`, `:androidApp:assembleDebug` (APK), and
      `:shared:compileKotlinIosSimulatorArm64`. Windows still cannot link or run the Xcode app.
      The build caught several issues reading had missed — all fixed:
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
      binds `IosAVPlayer`, and Web binds `WebPlatformPlayer`. All four are build-verified; iOS was
      link- and device-verified on 2026-08-21 (`:shared:iosSimulatorArm64Test` = 374 passing, signed
      `.app` installed on an iPad). Actual audio output on an iOS device is still unverified.
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
        `lyricMap` → `lyricIndex` + calls `lyricSurface.updateLyric(...)`.
      - **Build-verified:** `:shared:compileAndroidMain`, `:androidApp:compileDebugKotlin`,
        `:androidApp:assembleDebug` (APK), `:shared:compileKotlinJvm`, `:shared:jvmTest` all green.
- [x] **Desktop native media controls are implemented.** Windows SMTC uses direct JNA/COM WinRT
      interop without a helper DLL; Linux uses MPRIS. Both consume the shared metadata and player
      command pipeline. Windows has a real-HWND native regression test; JNA `Structure` types used
      across the module boundary must not be private because JDK 21 blocks reflective field access.
      Keep all Windows session creation, metadata/timeline updates, and release on the dedicated
      SMTC MTA thread so `RoInitialize` and `RoUninitialize` stay paired on one thread.
- [x] **iOS Live Activity source and Xcode target are wired, and the app now builds and installs
      on a real device** (2026-08-21, macOS 26 / Xcode 26.5). Kotlin publishes serial
      `LiveActivityData`; Swift drives ActivityKit and the LyricWidget extension renders Lock Screen
      and Dynamic Island content. `LyricWidget.appex` is embedded, signed, and installs. **Still
      unverified:** ActivityKit behaviour at runtime — the Live Activity is only observable on a
      device by hand, and the Dynamic Island layout needs Dynamic-Island-capable hardware.
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
- [x] **SQLDelight cache** (D3) — DONE (plugin + 11 tables + drivers + cache-then-network +
      PlayerStatus playback persistence + DownloadQueue task persistence; build-verified 2026-07-13).
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
