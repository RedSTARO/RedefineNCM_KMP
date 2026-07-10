import SwiftUI
import UIKit
import Shared

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        handleEventsForBackgroundURLSession identifier: String,
        completionHandler: @escaping () -> Void
    ) {
        let handled = DownloadServiceController.shared.handleBackgroundEvents(
            identifier: identifier,
            completionHandler: completionHandler
        )
        if !handled {
            completionHandler()
        }
    }
}

@main
@MainActor
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

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
