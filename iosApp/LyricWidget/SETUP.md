# LyricWidget — iOS Live Activity (灵动岛 / Dynamic Island) setup

The **source** for the iOS Live Activity is complete and lives here + in the app target. What
remains is the **Xcode project wiring**, which must be done in Xcode (it edits
`iosApp.xcodeproj/project.pbxproj` — not reliably editable by hand, and unverifiable without a
build on macOS).

## What's already written (source-complete)

| File | Target it belongs to | Purpose |
|---|---|---|
| `LyricWidget/LyricActivityAttributes.swift` | **both** app + widget | Shared `ActivityAttributes` + `ContentState` (mirrors Kotlin `LiveActivityData`) |
| `LyricWidget/LyricLiveActivity.swift` | widget | Lock Screen + Dynamic Island UI (`ActivityConfiguration`) |
| `LyricWidget/LyricWidgetBundle.swift` | widget | `@main WidgetBundle` entry point |
| `LyricWidget/Info.plist` | widget | `NSExtensionPointIdentifier = com.apple.widgetkit-extension` |
| `iosApp/LiveActivityManager.swift` | app | Observes Kotlin `LyricNotificationController` → `Activity.request/update/end` |
| `iosApp/iOSApp.swift` (edited) | app | Calls `LiveActivityManager.shared.startObserving()` at launch |
| `iosApp/Info.plist` (edited) | app | `NSSupportsLiveActivities = true` |

Kotlin side: `LyricNotificationController` (iosMain) now exposes `startObserving(onChange:)` /
`stopObserving()`. The playback/lyric pipeline still needs to call `updateLyric(...)` to feed it
(same gap as every other platform's now-playing surface).

## Xcode steps (build-gated — do these on macOS with Xcode 16+)

1. **File ▸ New ▸ Target… ▸ Widget Extension.** Name it `LyricWidget`. Uncheck "Include
   Configuration App Intent" and "Include Live Activity" only if you intend to replace the
   generated files — otherwise let it generate, then delete its template `.swift` and add the
   files in this folder.
2. **Add files to the right targets:**
   - `LyricActivityAttributes.swift` → **both** `iosApp` and `LyricWidget` (Target Membership).
   - `LyricLiveActivity.swift`, `LyricWidgetBundle.swift`, `Info.plist` → `LyricWidget` only.
   - `LiveActivityManager.swift` → `iosApp` only (it already links the `Shared` KMP framework).
3. **Set the widget's Info.plist** to `LyricWidget/Info.plist` (Build Settings ▸ Info.plist File).
4. Confirm the app target's Info.plist has `NSSupportsLiveActivities = true` (already added).
5. **Deployment target:** the Live Activity code is gated to iOS 16.2+; the app target may keep a
   lower minimum — the `@available(iOS 16.2, *)` guards handle older devices (no Live Activity).
6. The widget extension does **not** need the `Shared` framework (it only uses the Codable
   `ContentState`). Only the app target links `Shared`.

## Notes / TODO

- **No App Group required** for text: data flows via ActivityKit `ContentState`. Album artwork
  inside the Live Activity would need App-Group image caching (the widget process can't fetch
  remote URLs) — deferred.
- Live Activities require a real device or a simulator that supports them; the Dynamic Island
  rendering only appears on Dynamic-Island-capable devices (others get the Lock Screen banner).
