import SwiftUI
import AppKit

struct SettingsView: View {
    @ObservedObject var model: AppModel
    private var version: String { Bundle.main.object(forInfoDictionaryKey: "DropDuoVersion") as? String ?? "development" }

    var body: some View {
        Form {
            Section {
                Toggle("Accept files and text from paired devices", isOn: $model.receiving)
                    .onChange(of: model.receiving) { _, _ in model.persistSettings() }
            } footer: {
                Text("Only devices you approved can connect. Pausing keeps them paired.").foregroundStyle(.secondary)
            }
            Section {
                LabeledContent("Received files") {
                    Button("Show in Finder") { NSWorkspace.shared.open(model.inboxRoot) }
                }
            } footer: {
                Text("Saved in Downloads/DropDuo, in a folder for each device.").foregroundStyle(.secondary)
            }
            Section {
                LabeledContent("Recent activity") {
                    Button("Clear History") { model.clearHistory() }.disabled(model.transfers.isEmpty)
                }
            } footer: {
                Text("Keeps the latest 100 transfers. Clearing it doesn't delete any files.").foregroundStyle(.secondary)
            }
            Section {
                LabeledContent("Version", value: version)
            } footer: {
                Text("Local network only. No accounts, no cloud, no analytics. While your Mac sleeps, it can't receive.").foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped).frame(width: 460).fixedSize(horizontal: false, vertical: true)
    }
}
