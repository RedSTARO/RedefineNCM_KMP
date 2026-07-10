import ActivityKit
import Foundation
import Shared

/// Live Activity attributes (mirrors LyricWidget/LyricActivityAttributes.swift).
/// Belongs to the iosApp target so `LiveActivityManager` can reference the type.
struct LyricActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        var title: String; var artist: String; var currentLyric: String; var nextLyric: String
        var isPlaying: Bool
        var positionMs: Int64; var durationMs: Int64
    }
    var appName: String = "RedefineNCM"
}


/// Bridges the shared Kotlin `LyricNotificationController` (iOS actual) to ActivityKit.
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
    private var generation: UInt64 = 0
    private var workerID: UInt64 = 0
    private var stopped = false
    private var didRecoverActivities = false

    private enum Operation {
        case content(LyricActivityAttributes.ContentState)
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
        startWorkerIfNeeded()
        LyricNotificationController.shared.startObserving { [weak self] data in
            Task { @MainActor [weak self] in self?.handle(data) }
        }
    }

    func stopObserving() {
        LyricNotificationController.shared.stopObserving()
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

    private func handle(_ data: LiveActivityData?) {
        let operation: Operation
        if let data, !data.currentLyric.isEmpty {
            operation = .content(
                LyricActivityAttributes.ContentState(
                    title: data.title,
                    artist: data.artist,
                    currentLyric: data.currentLyric,
                    nextLyric: data.nextLyric,
                    isPlaying: data.isPlaying,
                    positionMs: data.positionMs,
                    durationMs: data.durationMs
                )
            )
        } else {
            operation = .end
        }
        generation &+= 1
        pendingUpdate = PendingUpdate(generation: generation, operation: operation)
        startWorkerIfNeeded()
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
            case .content(let state):
                await apply(state, generation: update.generation)
            case .end:
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
        generation expectedGeneration: UInt64
    ) async {
        guard expectedGeneration == generation, !Task.isCancelled else { return }
        if let activity {
            await activity.update(ActivityContent(state: state, staleDate: nil))
            return
        }
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

    private func recoverExistingActivitiesIfNeeded() async {
        guard !didRecoverActivities else { return }
        let existing = Activity<LyricActivityAttributes>.activities
        let primary = activity ?? existing.first
        activity = primary
        for extra in existing where extra.id != primary?.id {
            if Task.isCancelled { return }
            await extra.end(nil, dismissalPolicy: .immediate)
        }
        guard !Task.isCancelled else { return }
        didRecoverActivities = true
    }

    private func endCurrentActivity() async {
        guard let current = activity else { return }
        activity = nil
        await current.end(nil, dismissalPolicy: .immediate)
    }
}
