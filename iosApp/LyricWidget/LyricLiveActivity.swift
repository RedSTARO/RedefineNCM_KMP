import ActivityKit
import SwiftUI
import WidgetKit

/// The Live Activity UI: Lock Screen / banner presentation plus the Dynamic Island
/// (compact, minimal, and expanded) renderings of the current lyric line.
///
/// Belongs to the `LyricWidget` extension target. Requires iOS 16.2+ (ActivityKit
/// `ActivityConfiguration`); the Dynamic Island itself appears on devices that have it.
@available(iOS 16.2, *)
struct LyricLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: LyricActivityAttributes.self) { context in
            // Lock Screen / banner presentation.
            LockScreenLyricView(state: context.state, playing: context.showsPlaying)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .activityBackgroundTint(Color.black.opacity(0.55))
                .activitySystemActionForegroundColor(Color.white)
                .widgetURL(URL(string: "redefinencm://nowplaying"))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Image(systemName: context.showsPlaying ? "play.fill" : "pause.fill")
                        .font(.title3)
                        .foregroundStyle(.pink)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Image(systemName: "waveform")
                        .font(.title3)
                        .foregroundStyle(.pink)
                }
                DynamicIslandExpandedRegion(.center) {
                    Text(context.state.title.isEmpty ? "RedefineNCM" : context.state.title)
                        .font(.caption)
                        .fontWeight(.semibold)
                        .lineLimit(1)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(spacing: 2) {
                        if let lyric = context.state.distinctLyric {
                            Text(lyric)
                                .font(.headline)
                                .fontWeight(.bold)
                                .lineLimit(1)
                                .minimumScaleFactor(0.7)
                        }
                        if !context.state.nextLyric.isEmpty {
                            Text(context.state.nextLyric)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                        LyricProgressView(state: context.state, playing: context.showsPlaying)
                    }
                    .frame(maxWidth: .infinity)
                }
            } compactLeading: {
                Image(systemName: context.showsPlaying ? "play.fill" : "pause.fill")
                    .foregroundStyle(.pink)
            } compactTrailing: {
                Text(context.state.currentLyric)
                    .font(.caption2)
                    .lineLimit(1)
                    .frame(maxWidth: 96)
            } minimal: {
                Image(systemName: context.showsPlaying ? "play.fill" : "pause.fill")
                    .foregroundStyle(.pink)
            }
            .widgetURL(URL(string: "redefinencm://nowplaying"))
            .keylineTint(.pink)
        }
    }
}

@available(iOS 16.2, *)
private extension ActivityViewContext where Attributes == LyricActivityAttributes {
    /// Playing, as far as the last update says. A stale update means the app stopped sending
    /// them (it was closed or died), so nothing is known to be playing.
    var showsPlaying: Bool { state.isPlaying && !isStale }
}

@available(iOS 16.2, *)
private extension LyricActivityAttributes.ContentState {
    /// The lyric line, or nil when the app sent the title in its place because the track has no
    /// line at this moment. The title is already shown above it, so it would appear twice.
    var distinctLyric: String? {
        if currentLyric.isEmpty { return nil }
        // An activity from an older build carries no flag; it compares the texts instead.
        let isTitle = lyricIsTitle ?? (currentLyric == title)
        return isTitle ? nil : currentLyric
    }

    /// The span the progress bar runs over while playing, in wall-clock time.
    var progressRange: ClosedRange<Date>? {
        guard let start = progressStart, durationMs > 0 else { return nil }
        let end = start.addingTimeInterval(Double(durationMs) / 1000)
        return end > start ? start...end : nil
    }
}

/// The track's progress. While playing it moves by itself from the update's start time, so the
/// app sends no update just to advance it; paused or stale, it stays where the update left it.
@available(iOS 16.2, *)
private struct LyricProgressView: View {
    let state: LyricActivityAttributes.ContentState
    let playing: Bool

    var body: some View {
        if state.durationMs > 0 {
            if playing, let range = state.progressRange {
                ProgressView(
                    timerInterval: range,
                    countsDown: false,
                    label: { EmptyView() },
                    currentValueLabel: { EmptyView() }
                )
                .tint(.pink)
            } else {
                ProgressView(
                    value: min(Double(state.positionMs), Double(state.durationMs)),
                    total: Double(state.durationMs)
                )
                .tint(.pink)
            }
        }
    }
}

@available(iOS 16.2, *)
private struct LockScreenLyricView: View {
    let state: LyricActivityAttributes.ContentState
    let playing: Bool

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "music.note.list")
                .font(.title2)
                .foregroundStyle(.pink)
            VStack(alignment: .leading, spacing: 4) {
                Text(state.title.isEmpty ? "RedefineNCM" : state.title)
                    .font(.subheadline)
                    .fontWeight(.bold)
                    .lineLimit(1)
                if let lyric = state.distinctLyric {
                    Text(lyric)
                        .font(.body)
                        .fontWeight(.semibold)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                }
                if !state.nextLyric.isEmpty {
                    Text(state.nextLyric)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                LyricProgressView(state: state, playing: playing)
            }
            Spacer(minLength: 0)
        }
    }
}
