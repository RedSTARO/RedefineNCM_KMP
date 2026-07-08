# LyricWidget — iOS Live Activity (灵动岛 / Dynamic Island) setup

The iOS Live Activity source and Xcode project wiring live in this repo. The `LyricWidget`
extension target is registered in `iosApp.xcodeproj/project.pbxproj`, embedded by the `iosApp`
target, and renders the ActivityKit `ContentState` pushed by `LiveActivityManager`.

## Wired files

| File | Target it belongs to | Purpose |
|---|---|---|
| `LyricWidget/LyricActivityAttributes.swift` | widget | Widget-side `ActivityAttributes` + `ContentState` (mirrors Kotlin `LiveActivityData`) |
| `LyricWidget/LyricLiveActivity.swift` | widget | Lock Screen + Dynamic Island UI (`ActivityConfiguration`) |
| `LyricWidget/LyricWidgetBundle.swift` | widget | `@main WidgetBundle` entry point |
| `LyricWidget/Info.plist` | widget | `NSExtensionPointIdentifier = com.apple.widgetkit-extension` |
| `iosApp/LiveActivityManager.swift` | app | App-side `ActivityAttributes`; observes Kotlin `LyricNotificationController` → `Activity.request/update/end` |
| `iosApp/iOSApp.swift` (edited) | app | Calls `LiveActivityManager.shared.startObserving()` at launch |
| `iosApp/Info.plist` (edited) | app | `NSSupportsLiveActivities = true` |

Kotlin side: `LyricNotificationController` (iosMain) now exposes `startObserving(onChange:)` /
`stopObserving()` and the playback/lyric pipeline feeds it through `updateLyric(...)`.

## macOS validation

Windows can compile the Kotlin iOS framework but cannot run Xcode or ActivityKit validation.
On macOS with Xcode 16+, build the `iosApp` scheme and confirm:

1. `LyricWidget.appex` is embedded under the app's PlugIns directory.
2. Starting playback creates or updates one `Activity<LyricActivityAttributes>`.
3. Clearing playback ends the activity.
4. Lock Screen rendering appears on any supported device/simulator; Dynamic Island rendering only
   appears on Dynamic-Island-capable devices.

## Notes / TODO

- **No App Group required** for text: data flows via ActivityKit `ContentState`. Album artwork
  inside the Live Activity would need App-Group image caching (the widget process can't fetch
  remote URLs) — deferred.
- Live Activities require a real device or a simulator that supports them; the Dynamic Island
  rendering only appears on Dynamic-Island-capable devices (others get the Lock Screen banner).
