import UIKit
import SwiftUI
import Shared
import UniformTypeIdentifiers

private extension Notification.Name {
    static let requestSettingsImport = Notification.Name("RedefineNCMRequestSettingsImport")
    static let settingsImported = Notification.Name("RedefineNCMSettingsImported")
    static let requestSettingsExport = Notification.Name("RedefineNCMRequestSettingsExport")
}

private struct SettingsBackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    let json: String

    init(json: String) {
        self.json = json
    }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents,
              let text = String(data: data, encoding: .utf8) else {
            throw CocoaError(.fileReadCorruptFile)
        }
        json = text
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(json.utf8))
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    @State private var importingSettings = false
    @State private var exportingSettings = false
    @State private var exportDocument = SettingsBackupDocument(json: "{}")

    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            .onOpenURL { url in
                guard url.scheme?.lowercased() == "redefinencm",
                      url.host?.lowercased() == "nowplaying" else { return }
                AppNavigationRequests.shared.openNowPlaying()
            }
            .onReceive(NotificationCenter.default.publisher(for: .requestSettingsImport)) { _ in
                importingSettings = true
            }
            .fileImporter(
                isPresented: $importingSettings,
                allowedContentTypes: [.json],
                allowsMultipleSelection: false
            ) { result in
                guard case .success(let urls) = result, let url = urls.first else { return }
                let accessing = url.startAccessingSecurityScopedResource()
                defer { if accessing { url.stopAccessingSecurityScopedResource() } }
                guard let json = try? String(contentsOf: url, encoding: .utf8) else { return }
                NotificationCenter.default.post(
                    name: .settingsImported,
                    object: nil,
                    userInfo: ["json": json]
                )
            }
            .onReceive(NotificationCenter.default.publisher(for: .requestSettingsExport)) { note in
                guard let json = note.userInfo?["json"] as? String else { return }
                exportDocument = SettingsBackupDocument(json: json)
                exportingSettings = true
            }
            .fileExporter(
                isPresented: $exportingSettings,
                document: exportDocument,
                contentType: .json,
                defaultFilename: "RedefineNCM_KMP_settings"
            ) { _ in }
    }
}
