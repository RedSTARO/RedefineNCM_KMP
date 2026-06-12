import SwiftUI
import WidgetKit

/// Entry point for the `LyricWidget` extension target. Only the Live Activity is registered.
@main
struct LyricWidgetBundle: WidgetBundle {
    var body: some Widget {
        if #available(iOS 16.2, *) {
            LyricLiveActivity()
        }
    }
}
