import ActivityKit
import Foundation
import Shared
import UIKit

/// Bridges the shared Kotlin `IosLiveActivity` surface to ActivityKit.
///
/// Observes the lyric data stream from Kotlin and starts / updates / ends the Live Activity that
/// the `LyricWidget` extension renders on the Lock Screen and in the Dynamic Island (灵动岛).
///
/// Belongs to the main app target (`iosApp`), which already links the `Shared` framework.
@available(iOS 16.2, *)
@MainActor
final class LiveActivityManager {
    static let shared = LiveActivityManager()
    private var activity: Activity<LyricActivityAttributes>?
    private var pendingUpdate: PendingUpdate?
    private var updateTask: Task<Void, Never>?
    private var stateTask: Task<Void, Never>?
    private var generation: UInt64 = 0
    private var workerID: UInt64 = 0
    private var stopped = false
    private var didRecoverActivities = false
    private var lifecycleObservers: [NSObjectProtocol] = []

    /// The last value Kotlin sent and when, applied again when the app returns to the foreground.
    private var lastData: LiveActivityData?
    private var lastDataReceivedAt = Date()

    /// The track the current activity last showed.
    private var activityTrack: String?
    /// The current activity reached `.ended` (the system's eight-hour limit) before it was removed.
    private var activityEnded = false
    /// The track whose activity the person removed: none is started again until another plays.
    private var dismissedTrack: String?

    /// How long an update stays current while playing. Kotlin sends one at least every minute
    /// while playing, so an activity left behind by an app that died turns stale and shows paused.
    private static let playingStaleInterval: TimeInterval = 90

    private enum Operation {
        case content(LyricActivityAttributes.ContentState, track: String)
        case end
    }

    private struct PendingUpdate {
        let generation: UInt64
        let operation: Operation
    }

    private init() {}

    /// Begin observing the Kotlin lyric stream. Call once at app startup (see `iOSApp`).
    func startObserving() {
        stopped = false
        observeLifecycleIfNeeded()
        startWorkerIfNeeded()
        IosLiveActivity.shared.startObserving { [weak self] data in
            Task { @MainActor [weak self] in self?.handle(data) }
        }
    }

    func stopObserving() {
        IosLiveActivity.shared.stopObserving()
        stopped = true
        generation &+= 1
        pendingUpdate = nil

        let stoppingGeneration = generation
        let previousTask = updateTask
        previousTask?.cancel()
        workerID &+= 1
        let stoppingWorkerID = workerID
        updateTask = Task { @MainActor [weak self] in
            _ = await previousTask?.value
            guard let self else { return }
            defer { self.finishWorker(stoppingWorkerID) }
            guard !Task.isCancelled,
                  self.generation == stoppingGeneration else { return }
            await self.recoverExistingActivitiesIfNeeded()
            await self.endCurrentActivity()
        }
    }

    private func observeLifecycleIfNeeded() {
        guard lifecycleObservers.isEmpty else { return }
        let center = NotificationCenter.default
        // An activity outlives the process. It is ended here when the app is closed while it
        // runs, playing in the background; one left by a crash turns stale instead.
        lifecycleObservers.append(
            center.addObserver(
                forName: UIApplication.willTerminateNotification,
                object: nil,
                queue: .main
            ) { _ in
                LiveActivityManager.endAllActivitiesBeforeExit()
            }
        )
        // Requests are refused in the background. Back in the foreground the last value is
        // applied again, which starts an activity for a track that kept playing meanwhile.
        lifecycleObservers.append(
            center.addObserver(
                forName: UIApplication.didBecomeActiveNotification,
                object: nil,
                queue: .main
            ) { [weak self] _ in
                Task { @MainActor [weak self] in self?.reapplyLastData() }
            }
        )
    }

    /// Ends every activity, waiting up to two seconds: the process ends when this returns.
    private nonisolated static func endAllActivitiesBeforeExit() {
        let activities = Activity<LyricActivityAttributes>.activities
        guard !activities.isEmpty else { return }
        let done = DispatchSemaphore(value: 0)
        Task.detached {
            for activity in activities {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
            done.signal()
        }
        _ = done.wait(timeout: .now() + 2)
    }

    private func reapplyLastData() {
        guard !stopped, let data = lastData else { return }
        handle(data, receivedAt: lastDataReceivedAt)
    }

    private func handle(_ data: LiveActivityData?, receivedAt: Date = Date()) {
        lastData = data
        lastDataReceivedAt = receivedAt
        let operation: Operation
        if let data, !data.currentLyric.isEmpty {
            let position = data.durationMs > 0
                ? min(max(data.positionMs, 0), data.durationMs)
                : max(data.positionMs, 0)
            operation = .content(
                LyricActivityAttributes.ContentState(
                    title: data.title,
                    artist: data.artist,
                    currentLyric: data.currentLyric,
                    nextLyric: data.nextLyric,
                    isPlaying: data.isPlaying,
                    positionMs: position,
                    durationMs: data.durationMs,
                    lyricIsTitle: data.lyricIsTitle,
                    // The widget moves the progress bar from here, so the app sends no update
                    // just to advance it.
                    progressStart: receivedAt.addingTimeInterval(-Double(position) / 1000)
                ),
                track: Self.trackKey(of: data)
            )
        } else {
            operation = .end
        }
        generation &+= 1
        pendingUpdate = PendingUpdate(generation: generation, operation: operation)
        startWorkerIfNeeded()
    }

    private static func trackKey(of data: LiveActivityData) -> String {
        "\(data.title)\u{1F}\(data.artist)"
    }

    private func startWorkerIfNeeded() {
        guard updateTask == nil,
              (pendingUpdate != nil || !didRecoverActivities),
              !stopped else { return }
        workerID &+= 1
        let currentWorkerID = workerID
        updateTask = Task { @MainActor [weak self] in
            await self?.drainUpdates(workerID: currentWorkerID)
        }
    }

    private func drainUpdates(workerID: UInt64) async {
        await recoverExistingActivitiesIfNeeded()
        while !Task.isCancelled, let update = pendingUpdate {
            pendingUpdate = nil
            guard update.generation == generation else { continue }
            switch update.operation {
            case .content(let state, let track):
                await apply(state, track: track, generation: update.generation)
            case .end:
                // A queue cleared ends the activity, and the next queue starts afresh.
                dismissedTrack = nil
                await endCurrentActivity()
            }
        }
        finishWorker(workerID)
    }

    private func finishWorker(_ finishedWorkerID: UInt64) {
        guard workerID == finishedWorkerID else { return }
        updateTask = nil
        startWorkerIfNeeded()
    }

    private func apply(
        _ state: LyricActivityAttributes.ContentState,
        track: String,
        generation expectedGeneration: UInt64
    ) async {
        guard expectedGeneration == generation, !Task.isCancelled else { return }
        if let current = activity {
            switch current.activityState {
            case .active, .stale:
                await current.update(ActivityContent(state: state, staleDate: staleDate(for: state)))
                activityTrack = track
                return
            case .ended:
                // The system ends an activity after about eight hours, and an ended one takes no
                // more updates. A new one is started below.
                forgetActivity()
            case .dismissed:
                // Removed from the Lock Screen. Unless it had ended first, the person removed it:
                // none is started again for that track, only for the next one.
                if !activityEnded { dismissedTrack = activityTrack ?? track }
                forgetActivity()
            @unknown default:
                return
            }
        }
        if let dismissed = dismissedTrack {
            guard dismissed != track else { return }
            dismissedTrack = nil
        }
        // Only playback starts an activity: a queue restored at launch is paused, and an activity
        // for it would stay on the Lock Screen while nothing plays. iOS refuses a request from the
        // background, so none is made there either.
        guard state.isPlaying,
              UIApplication.shared.applicationState != .background,
              ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        do {
            let started = try Activity.request(
                attributes: LyricActivityAttributes(),
                content: ActivityContent(state: state, staleDate: staleDate(for: state)),
                pushType: nil
            )
            adopt(started, track: track)
        } catch {
            print("LiveActivity request failed: \(error.localizedDescription)")
        }
    }

    private func staleDate(for state: LyricActivityAttributes.ContentState) -> Date? {
        state.isPlaying ? Date().addingTimeInterval(Self.playingStaleInterval) : nil
    }

    /// Makes `adopted` the current activity and follows its state, which is how the manager
    /// learns that the person removed it.
    private func adopt(_ adopted: Activity<LyricActivityAttributes>, track: String?) {
        stateTask?.cancel()
        activity = adopted
        activityTrack = track
        activityEnded = false
        stateTask = Task { @MainActor [weak self] in
            for await state in adopted.activityStateUpdates {
                guard let self, self.activity?.id == adopted.id else { return }
                switch state {
                case .ended:
                    self.activityEnded = true
                case .dismissed:
                    if !self.activityEnded { self.dismissedTrack = self.activityTrack }
                    self.forgetActivity()
                    return
                default:
                    break
                }
            }
        }
    }

    private func forgetActivity() {
        stateTask?.cancel()
        stateTask = nil
        activity = nil
        activityTrack = nil
        activityEnded = false
    }

    private func recoverExistingActivitiesIfNeeded() async {
        guard !didRecoverActivities else { return }
        let existing = Activity<LyricActivityAttributes>.activities
        // An activity left over from the last run is adopted only if it can still be updated.
        let primary = activity ?? existing.first(where: { candidate in
            candidate.activityState == .active || candidate.activityState == .stale
        })
        if let primary, primary.id != activity?.id {
            adopt(primary, track: nil)
        }
        for extra in existing where extra.id != primary?.id {
            if Task.isCancelled { return }
            await extra.end(nil, dismissalPolicy: .immediate)
        }
        guard !Task.isCancelled else { return }
        didRecoverActivities = true
    }

    private func endCurrentActivity() async {
        guard let current = activity else { return }
        forgetActivity()
        await current.end(nil, dismissalPolicy: .immediate)
    }
}
