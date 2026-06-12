import SwiftUI

@main
struct iOSApp: App {
    init() {
        // Bridge the shared Kotlin lyric stream to ActivityKit (Dynamic Island + Lock Screen).
        if #available(iOS 16.2, *) {
            LiveActivityManager.shared.startObserving()
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}