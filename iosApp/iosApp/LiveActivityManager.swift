import ActivityKit
import Foundation
import Shared

/// Bridges the shared Kotlin `LyricNotificationController` (iOS actual) to ActivityKit.
///
/// Observes the lyric data stream from Kotlin and starts / updates / ends the Live Activity that
/// the `LyricWidget` extension renders on the Lock Screen and in the Dynamic Island (灵动岛).
///
/// Belongs to the main app target (`iosApp`), which already links the `Shared` framework.
@available(iOS 16.2, *)
final class LiveActivityManager {
    static let shared = LiveActivityManager()
    private var activity: Activity<LyricActivityAttributes>?
    private init() {}

    /// Begin observing the Kotlin lyric stream. Call once at app startup (see `iOSApp`).
    func startObserving() {
        LyricNotificationController.shared.startObserving { [weak self] data in
            // Kotlin emits on Dispatchers.Main; hop to the main queue defensively for UIKit/ActivityKit.
            DispatchQueue.main.async { self?.handle(data) }
        }
    }

    func stopObserving() {
        LyricNotificationController.shared.stopObserving()
        end()
    }

    private func handle(_ data: LiveActivityData?) {
        guard let data, !data.currentLyric.isEmpty else {
            end()
            return
        }
        let state = LyricActivityAttributes.ContentState(
            title: data.title,
            artist: data.artist,
            currentLyric: data.currentLyric,
            nextLyric: data.nextLyric
        )
        if let activity {
            Task { await activity.update(ActivityContent(state: state, staleDate: nil)) }
        } else {
            start(with: state)
        }
    }

    private func start(with state: LyricActivityAttributes.ContentState) {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        do {
            activity = try Activity.request(
                attributes: LyricActivityAttributes(),
                content: ActivityContent(state: state, staleDate: nil),
                pushType: nil
            )
        } catch {
            print("LiveActivity request failed: \(error.localizedDescription)")
        }
    }

    private func end() {
        guard let current = activity else { return }
        activity = nil
        Task { await current.end(nil, dismissalPolicy: .immediate) }
    }
}
