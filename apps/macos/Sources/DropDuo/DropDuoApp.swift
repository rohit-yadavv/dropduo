import SwiftUI
import AppKit
import DropDuoCore

@main struct DropDuoApp: App {
    @StateObject private var model = AppModel()
    var body: some Scene {
        WindowGroup("DropDuo", id: "main") { MainView(model: model).frame(minWidth: 720, minHeight: 520).tint(Brand.cobalt) }
            .defaultSize(width: 920, height: 640)
            .commands { CommandGroup(replacing: .newItem) {} }
        Settings { SettingsView(model: model).tint(Brand.cobalt) }
        MenuBarExtra {
            MenuContent(model: model)
        } label: { Image(nsImage: Brand.menuIcon).accessibilityLabel("DropDuo") }
    }
}

struct MenuContent: View {
    @ObservedObject var model: AppModel
    @Environment(\.openWindow) private var openWindow
    var body: some View {
        if let device = model.selectedDevice {
            Text(model.isOnline(device) ? "\(device.name) · Connected" : "\(device.name) · Not reachable")
        } else { Text("No paired devices") }
        Button("Open DropDuo") { openWindow(id: "main"); NSApp.activate(ignoringOtherApps: true) }
        Button("Send files…") { model.chooseFiles() }.disabled(!model.online.contains(model.selected))
        Button("Open received files") { NSWorkspace.shared.open(model.inboxRoot) }
        Divider()
        SettingsLink { Text("Settings…") }
        Button("Quit DropDuo") { NSApp.terminate(nil) }
    }
}

struct MainView: View {
    @ObservedObject var model: AppModel
    var body: some View {
        NavigationSplitView {
            Sidebar(model: model).navigationSplitViewColumnWidth(min: 220, ideal: 240, max: 320)
        } detail: {
            Group {
                if let device = model.selectedDevice { DeviceView(model: model, device: device).id(device.id) }
                else { WelcomeView(model: model) }
            }.seamlessToolbar()
        }
        .sheet(isPresented: Binding(get: { model.ticket != nil }, set: { if !$0 { model.cancelPairing() } })) { PairingView(model: model).tint(Brand.cobalt) }
        .alert("DropDuo", isPresented: Binding(get: { model.error != nil }, set: { if !$0 { model.error = nil } })) { Button("OK") { model.error = nil } } message: { Text(model.error ?? "") }
        .confirmationDialog("Forget \(model.confirmation?.name ?? "this device")?", isPresented: Binding(get: { model.confirmation != nil }, set: { if !$0 { model.confirmation = nil } })) {
            if let device = model.confirmation { Button("Forget Device", role: .destructive) { model.forget(device); model.confirmation = nil } }
        } message: { Text("It will need to pair again before it can share with this Mac. Received files stay in Downloads.") }
    }
}

struct Sidebar: View {
    @ObservedObject var model: AppModel
    var body: some View {
        List(selection: Binding(get: { model.selected.isEmpty ? nil : model.selected }, set: { model.selected = $0 ?? model.selected })) {
            if !model.devices.isEmpty {
                Section("Devices") {
                    ForEach(model.devices) { device in
                        DeviceRow(device: device, online: model.isOnline(device)).tag(device.id)
                            .contextMenu { Button("Forget Device…", role: .destructive) { model.confirmation = device } }
                    }
                }
            }
        }
        .listStyle(.sidebar)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(alignment: .leading, spacing: 10) {
                Button { model.makeTicket() } label: { Label("Pair a Device", systemImage: "plus.circle") }
                    .buttonStyle(.borderless).controlSize(.large)
                HStack(spacing: 6) {
                    Circle().fill(model.receiving ? Color.green : Color.orange).frame(width: 6, height: 6)
                    Text(model.receiving ? model.status : "Receiving paused").lineLimit(2)
                }.font(.caption).foregroundStyle(.secondary).accessibilityElement(children: .combine)
            }
            .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 16).padding(.vertical, 12)
        }
    }
}

struct DeviceRow: View {
    let device: Device
    let online: Bool
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "smartphone").font(.system(size: 15, weight: .medium)).frame(width: 30, height: 30)
                .background(.quaternary, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                .overlay(alignment: .bottomTrailing) {
                    Circle().fill(online ? Color.green : Color.secondary.opacity(0.5)).frame(width: 9, height: 9)
                        .overlay(Circle().stroke(Color(nsColor: .windowBackgroundColor), lineWidth: 2)).offset(x: 2, y: 2)
                }
            VStack(alignment: .leading, spacing: 1) {
                Text(device.name).lineLimit(1)
                Text(online ? "Connected" : "Not reachable").font(.caption).foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 3)
        .accessibilityElement(children: .combine)
    }
}

struct WelcomeView: View {
    @ObservedObject var model: AppModel
    var body: some View {
        VStack(spacing: 28) {
            Image(nsImage: Brand.mark).resizable().frame(width: 72, height: 72)
                .shadow(color: .black.opacity(0.12), radius: 8, y: 3).accessibilityHidden(true)
            VStack(spacing: 8) {
                Text("Pair your Android phone").font(.largeTitle.weight(.semibold))
                Text("Share files, photos, links and text over your own network.\nNo cables, accounts or cloud.")
                    .multilineTextAlignment(.center).foregroundStyle(.secondary)
            }
            VStack(alignment: .leading, spacing: 14) {
                Step(number: 1, text: "Install DropDuo on your Android phone.")
                Step(number: 2, text: "Connect both devices to the same Wi-Fi.")
                Step(number: 3, text: "Scan the pairing code, then approve it here.")
            }
            Button { model.makeTicket() } label: { Text("Show Pairing Code").padding(.horizontal, 8) }
                .buttonStyle(.borderedProminent).controlSize(.large).keyboardShortcut(.defaultAction)
        }
        .padding(40).frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    private struct Step: View {
        let number: Int; let text: String
        var body: some View {
            HStack(spacing: 12) {
                Text("\(number)").font(.callout.weight(.semibold).monospacedDigit()).foregroundStyle(Brand.cobalt)
                    .frame(width: 26, height: 26).background(Brand.cobalt.opacity(0.12), in: Circle())
                Text(text)
            }
        }
    }
}

private extension View {
    /// macOS 26+ draws no title-bar separator; content scrolls under the toolbar's edge effect instead.
    /// Without this, the detail toolbar's separator overhangs the sidebar divider by a few points.
    @ViewBuilder func seamlessToolbar() -> some View {
        if #available(macOS 26, *) { toolbarBackgroundVisibility(.hidden, for: .windowToolbar) } else { self }
    }
}
