# LyricWidget — iOS Live Activity (灵动岛 / Dynamic Island) setup

The iOS Live Activity source and Xcode project wiring live in this repo. The `LyricWidget`
extension target is registered in `iosApp.xcodeproj/project.pbxproj`, embedded by the `iosApp`
target, and renders the ActivityKit `ContentState` pushed by `LiveActivityManager`.

## Wired files

| File | Target it belongs to | Purpose |
|---|---|---|
| `LiveActivityShared/LyricActivityAttributes.swift` | app + widget | The only declaration of `ActivityAttributes` + `ContentState` (mirrors Kotlin `LiveActivityData`); both targets compile this one file |
| `LyricWidget/LyricLiveActivity.swift` | widget | Lock Screen + Dynamic Island UI (`ActivityConfiguration`) |
| `LyricWidget/LyricWidgetBundle.swift` | widget | `@main WidgetBundle` entry point |
| `LyricWidget/Info.plist` | widget | `NSExtensionPointIdentifier = com.apple.widgetkit-extension` |
| `iosApp/LiveActivityManager.swift` | app | Observes Kotlin `IosLiveActivity` and drives `Activity.request` / `update` / `end`; declares no attributes of its own |
| `iosApp/iOSApp.swift` (edited) | app | Calls `LiveActivityManager.shared.startObserving()` at launch |
| `iosApp/Info.plist` (edited) | app | `NSSupportsLiveActivities = true` |

Kotlin side: `IosLiveActivity` (iosMain) exposes `startObserving(onChange:)` /
`stopObserving()` and the playback/lyric pipeline feeds it through `updateLyric(...)`.

## macOS validation

Windows can compile the Kotlin iOS framework but cannot run Xcode or ActivityKit validation.
On macOS with Xcode 16+, build the `iosApp` scheme and confirm:

1. `LyricWidget.appex` is embedded under the app's PlugIns directory.
2. Starting playback creates or updates one `Activity<LyricActivityAttributes>`. A queue restored
   paused at launch creates none until play is pressed.
3. Clearing playback ends the activity. An activity the system ended (after about eight hours) is
   replaced at the next update while playing in the foreground, or when the app comes back to the
   foreground. One the person swiped away is not brought back for the same track; the next track
   starts a new one.
4. While playing, the progress bar moves without updates: the app sends one when the line, the
   play state or the track changes, after a seek, and once a minute. Each update while playing
   goes stale after 90 seconds, so an activity left by an app that died shows paused. Closing the
   app from the app switcher while it plays ends the activity.
5. Lock Screen rendering appears on any supported device/simulator; Dynamic Island rendering only
   appears on Dynamic-Island-capable devices.

## Notes / TODO

- **No App Group required** for text: data flows via ActivityKit `ContentState`. Album artwork
  inside the Live Activity would need App-Group image caching (the widget process can't fetch
  remote URLs) — deferred.
- Live Activities require a real device or a simulator that supports them; the Dynamic Island
  rendering only appears on Dynamic-Island-capable devices (others get the Lock Screen banner).
