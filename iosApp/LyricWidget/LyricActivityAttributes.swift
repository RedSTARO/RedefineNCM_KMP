import ActivityKit
import Foundation

/// Live Activity attributes for the now-playing lyric (灵动岛 / Dynamic Island + Lock Screen).
///
/// `ContentState` is the per-update dynamic data; it mirrors the Kotlin `LiveActivityData`
/// produced by the shared `LyricNotificationController`. The static part (`appName`) is set once
/// when the activity starts.
///
/// This file belongs to the widget target. The app target keeps a structurally identical mirror
/// in `LiveActivityManager.swift`; the two targets compile in separate products and therefore
/// cannot share a Swift source declaration directly.
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
