import SwiftUI
import AppKit

struct SettingsView: View {
    @ObservedObject var model: AppModel
    @ObservedObject var updater: AppUpdater
    private var version: String { Bundle.main.object(forInfoDictionaryKey: "DropDuoVersion") as? String ?? "development" }

    var body: some View {
        Form {
            Section {
                Toggle(isOn: Binding(get: { model.openAtLogin }, set: { model.setOpenAtLogin($0) })) {
                    Text("Open at login")
                    if let error = model.loginError { Text(error).foregroundStyle(.red) }
                }
                Toggle(isOn: $model.receiving) {
                    Text("Accept files and text")
                }
                .onChange(of: model.receiving) { _, _ in model.persistSettings() }
            }
            Section {
                LabeledContent {
                    Button("Show in Finder") { NSWorkspace.shared.open(model.inboxRoot) }
                } label: {
                    Text("Received files")
                    Text("Downloads › DropDuo")
                }
                LabeledContent {
                    Button("Clear History") { model.clearHistory() }.disabled(model.transfers.isEmpty)
                } label: {
                    Text("Recent activity")
                    Text("Received files are kept")
                }
            }
            Section {
                LabeledContent {
                    Button(updater.availableVersion == nil ? "Check for Updates…" : "Update…") { updater.check() }.disabled(!updater.canCheck)
                } label: {
                    HStack(spacing: 10) {
                        Image(nsImage: Brand.mark).resizable().frame(width: 30, height: 30).accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("DropDuo \(version)")
                            if let status = updateStatus { Text(status).font(.caption).foregroundStyle(.secondary) }
                        }
                    }
                }
            }
        }
        .onAppear { model.openAtLogin = LoginItem.isEnabled || LoginItem.needsApproval }
        .formStyle(.grouped).frame(width: 480).fixedSize(horizontal: false, vertical: true)
    }

    private var updateStatus: String? {
        if updater.waitingForTransfers { return "Installs when transfers finish" }
        if let available = updater.availableVersion { return "Version \(available) is available" }
        return updater.message
    }
}
