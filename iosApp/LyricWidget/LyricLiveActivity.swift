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
            LockScreenLyricView(state: context.state)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .activityBackgroundTint(Color.black.opacity(0.55))
                .activitySystemActionForegroundColor(Color.white)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Image(systemName: "music.note")
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
                        Text(context.state.currentLyric)
                            .font(.headline)
                            .fontWeight(.bold)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                        if !context.state.nextLyric.isEmpty {
                            Text(context.state.nextLyric)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            } compactLeading: {
                Image(systemName: "music.note")
                    .foregroundStyle(.pink)
            } compactTrailing: {
                Text(context.state.currentLyric)
                    .font(.caption2)
                    .lineLimit(1)
                    .frame(maxWidth: 96)
            } minimal: {
                Image(systemName: "music.note")
                    .foregroundStyle(.pink)
            }
            .widgetURL(URL(string: "redefinencm://nowplaying"))
            .keylineTint(.pink)
        }
    }
}

@available(iOS 16.2, *)
private struct LockScreenLyricView: View {
    let state: LyricActivityAttributes.ContentState

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
                Text(state.currentLyric)
                    .font(.body)
                    .fontWeight(.semibold)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                if !state.nextLyric.isEmpty {
                    Text(state.nextLyric)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 0)
        }
    }
}
