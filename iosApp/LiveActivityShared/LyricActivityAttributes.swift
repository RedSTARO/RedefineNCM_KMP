import ActivityKit
import Foundation

/// Live Activity attributes for the now-playing lyric (灵动岛 / Dynamic Island + Lock Screen).
///
/// `ContentState` is the per-update dynamic data; it mirrors the Kotlin `LiveActivityData`
/// produced by the shared `LyricNotificationController`. The static part (`appName`) is set once
/// when the activity starts.
///
/// This file is the single declaration of the type. It lives in its own synchronized folder so
/// that both the app target and the widget extension compile it: ActivityKit matches the app's
/// `Activity<LyricActivityAttributes>` against the widget's `ActivityConfiguration(for:)` by the
/// shape of `ContentState`, so a hand-mirrored second copy that drifts stops the Live Activity
/// from rendering with nothing at compile time to say so.
struct LyricActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        var title: String
        var artist: String
        var currentLyric: String
        var nextLyric: String
        var isPlaying: Bool
        var positionMs: Int64
        var durationMs: Int64
    }

    var appName: String = "RedefineNCM"
}
