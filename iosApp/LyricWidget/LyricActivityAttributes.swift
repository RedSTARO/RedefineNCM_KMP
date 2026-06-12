import ActivityKit
import Foundation

/// Live Activity attributes for the now-playing lyric (灵动岛 / Dynamic Island + Lock Screen).
///
/// `ContentState` is the per-update dynamic data; it mirrors the Kotlin `LiveActivityData`
/// produced by the shared `LyricNotificationController`. The static part (`appName`) is set once
/// when the activity starts.
///
/// IMPORTANT (Xcode setup): this file must belong to **both** targets — the main app
/// (`iosApp`) and the widget extension (`LyricWidget`) — so the same type is visible on both
/// sides. See `SETUP.md`.
struct LyricActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        var title: String
        var artist: String
        var currentLyric: String
        var nextLyric: String
    }

    var appName: String = "RedefineNCM"
}
