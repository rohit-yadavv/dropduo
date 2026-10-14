import SwiftUI
import AppKit
import DropDuoCore

@main struct DropDuoApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    var body: some Scene {
        WindowGroup("DropDuo", id: "main") { MainView(model: delegate.model, delegate: delegate).frame(minWidth: 720, minHeight: 520).tint(Brand.cobalt) }
            .defaultSize(width: 920, height: 640)
            .commands { CommandGroup(replacing: .newItem) {} }
        Settings { SettingsView(model: delegate.model, updater: delegate.updater).tint(Brand.cobalt) }
    }
}

@MainActor final class AppDelegate: NSObject, NSApplicationDelegate {
    let model = AppModel()
    let updater = AppUpdater()
    /// SwiftUI only opens windows from inside a view, so MainView hands these over when it first appears.
    var openWindow: (() -> Void)?
    var openSettings: (() -> Void)?
    weak var mainWindow: NSWindow?
    private var statusItem: StatusItemController?
    private var launchedAtLogin = false

    func applicationWillFinishLaunching(_ notification: Notification) { launchedAtLogin = LoginItem.launchedAtLogin }

    func applicationDidFinishLaunching(_ notification: Notification) {
        updater.start(model: model)
        let status = StatusItemController(model: model, updater: updater)
        status.openApp = { [weak self] in self?.showMain() }
        status.openSettings = { [weak self] in NSApp.activate(ignoringOtherApps: true); self?.openSettings?() }
        statusItem = status
        model.notifier.openApp = { [weak self] in self?.showMain() }
        model.windowVisible = { [weak self] in self?.mainWindow?.isVisible == true }
    }

    func showMain() {
        updater.checkAutomatically()
        NSApp.activate(ignoringOtherApps: true)
        if let window = mainWindow, window.isVisible { window.makeKeyAndOrderFront(nil) } else { openWindow?() }
    }

    /// Closing the window keeps DropDuo in the menu bar, so the phone can still connect.
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { false }
    func applicationDidBecomeActive(_ notification: Notification) { updater.checkAutomatically() }

    func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows flag: Bool) -> Bool {
        if !flag { showMain() }
        return true
    }

    func attach(_ window: NSWindow) {
        mainWindow = window
        // Opened by macOS at login: stay in the menu bar instead of popping a window.
        if launchedAtLogin { launchedAtLogin = false; window.close() }
    }
}

/// Reports the NSWindow hosting a SwiftUI view.
private struct WindowReader: NSViewRepresentable {
    let found: (NSWindow) -> Void
    func makeNSView(context: Context) -> NSView { Reader(found) }
    func updateNSView(_ view: NSView, context: Context) {}
    final class Reader: NSView {
        let found: (NSWindow) -> Void
        init(_ found: @escaping (NSWindow) -> Void) { self.found = found; super.init(frame: .zero) }
        required init?(coder: NSCoder) { nil }
        override func viewDidMoveToWindow() { super.viewDidMoveToWindow(); if let window { found(window) } }
    }
}

struct MainView: View {
    @ObservedObject var model: AppModel
    let delegate: AppDelegate
    @Environment(\.openWindow) private var openWindow
    @Environment(\.openSettings) private var openSettings
    /// Before the first pairing there's nothing to list, so the welcome screen gets the whole window.
    // `State` values rather than `@State`: Command Line Tools on macOS 27 ship without the SwiftUI macro plugin that `@State` needs.
    private let columns = State(initialValue: NavigationSplitViewVisibility.all)
    var body: some View {
        NavigationSplitView(columnVisibility: columns.projectedValue) {
            Sidebar(model: model, updater: delegate.updater).navigationSplitViewColumnWidth(min: 220, ideal: 248, max: 320)
        } detail: {
            Group {
                if let device = model.selectedDevice { DeviceView(model: model, device: device).id(device.id) }
                else { WelcomeView(model: model, updater: delegate.updater) }
            }.readableToolbar()
        }
        .onAppear { columns.wrappedValue = model.devices.isEmpty ? .detailOnly : .all }
        .onChange(of: model.devices.isEmpty) { _, empty in withAnimation { columns.wrappedValue = empty ? .detailOnly : .all } }
        .sheet(isPresented: Binding(get: { model.ticket != nil }, set: { if !$0 { model.cancelPairing() } })) { PairingView(model: model).tint(Brand.cobalt) }
        .alert("DropDuo", isPresented: Binding(get: { model.error != nil }, set: { if !$0 { model.error = nil } })) { Button("OK") { model.error = nil } } message: { Text(model.error ?? "") }
        .confirmationDialog("Forget \(model.confirmation?.name ?? "this device")?", isPresented: Binding(get: { model.confirmation != nil }, set: { if !$0 { model.confirmation = nil } })) {
            if let device = model.confirmation { Button("Forget Device", role: .destructive) { model.forget(device); model.confirmation = nil } }
        } message: { Text("It will need to pair again before it can share with this Mac. Received files stay in Downloads.") }
        .background(WindowReader { delegate.attach($0) })
        .onAppear {
            delegate.openWindow = { openWindow(id: "main") }
            delegate.openSettings = { openSettings() }
            #if DEBUG
            if ProcessInfo.processInfo.environment["DROPDUO_PREVIEW"] == "1",
               ProcessInfo.processInfo.environment["DROPDUO_PREVIEW_SCENE"] == "settings" { openSettings() }
            #endif
        }
    }
}

struct Sidebar: View {
    @ObservedObject var model: AppModel
    @ObservedObject var updater: AppUpdater
    var body: some View {
        List(selection: Binding(get: { model.selected.isEmpty ? nil : model.selected }, set: { model.selected = $0 ?? model.selected })) {
            Section("Devices") {
                ForEach(model.devices) { device in
                    DeviceRow(device: device, online: model.isOnline(device), activity: activity(device)).tag(device.id)
                        .contextMenu { Button("Forget Device…", role: .destructive) { model.confirmation = device } }
                }
                Button { model.makeTicket() } label: { AddDeviceRow() }.buttonStyle(.plain).help("Show a code to pair another phone")
            }
        }
        .listStyle(.sidebar)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(alignment: .leading, spacing: 12) {
                UpdateNotice(updater: updater)
                ReceiveStatus(model: model)
            }
            .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 14)
        }
    }

    /// What the row says under the device name while something is moving or waiting.
    private func activity(_ device: Device) -> String? {
        let mine = model.transfers.filter { $0.peer == device.id }
        if let active = mine.first(where: { ["Preparing", "Sending", "Receiving"].contains($0.state) }) {
            return active.state == "Preparing" ? "Preparing…" : "\(active.state) \(Int(active.progress * 100))%"
        }
        let waiting = mine.filter { $0.state == "Interrupted" && $0.autoRetry == true }.count
        return waiting == 0 ? nil : "\(waiting) waiting to send"
    }
}

struct DeviceRow: View {
    let device: Device
    let online: Bool
    let activity: String?
    var body: some View {
        HStack(spacing: 10) {
            // An explicit label color keeps the glyph dark on its white tile when the row is selected.
            Image(systemName: "smartphone").font(.system(size: 14)).foregroundStyle(Color(nsColor: .labelColor)).frame(width: 30, height: 30)
                .background(Brand.surface, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 9, style: .continuous).strokeBorder(Brand.hairline))
                .overlay(alignment: .bottomTrailing) {
                    Circle().fill(online ? Color.green : Color(nsColor: .tertiaryLabelColor)).frame(width: 9, height: 9)
                        .overlay(Circle().stroke(Color(nsColor: .windowBackgroundColor), lineWidth: 2)).offset(x: 2, y: 2)
                }
            VStack(alignment: .leading, spacing: 1) {
                Text(device.name).lineLimit(1)
                Text(activity ?? (online ? "Connected" : "Not reachable")).font(.caption).foregroundStyle(.secondary).monospacedDigit().lineLimit(1)
            }
        }
        .padding(.vertical, 3)
        .accessibilityElement(children: .combine)
    }
}

private struct AddDeviceRow: View {
    private let hover = State(initialValue: false)
    private var hovering: Bool { hover.wrappedValue }
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "plus").font(.system(size: 12, weight: .semibold)).foregroundStyle(hovering ? Brand.cobalt : .secondary)
                .frame(width: 30, height: 30)
                .overlay(RoundedRectangle(cornerRadius: 9, style: .continuous).strokeBorder(hovering ? Brand.cobalt.opacity(0.6) : Color.secondary.opacity(0.35), style: StrokeStyle(lineWidth: 1, dash: [3, 3])))
            Text("Pair a Device").foregroundStyle(hovering ? .primary : .secondary)
            Spacer(minLength: 0)
        }
        .padding(.vertical, 3).contentShape(Rectangle())
        .onHover { hover.wrappedValue = $0 }
        .animation(.easeOut(duration: 0.12), value: hovering)
    }
}

/// Whether this Mac can receive right now, with a one-click way back when receiving is paused.
struct ReceiveStatus: View {
    @ObservedObject var model: AppModel
    var body: some View {
        let ready = model.status.hasPrefix("Ready")
        HStack(alignment: .firstTextBaseline, spacing: 7) {
            Circle().fill(!model.receiving ? Color(nsColor: .tertiaryLabelColor) : ready ? Color.green : Color.orange).frame(width: 7, height: 7)
                .alignmentGuide(.firstTextBaseline) { $0[VerticalAlignment.center] + 3 }
            Text(model.receiving ? model.status : "Receiving is paused").lineLimit(3)
            if !model.receiving {
                Button("Turn On") { model.receiving = true; model.persistSettings() }.buttonStyle(.link)
            }
        }
        .font(.caption).foregroundStyle(.secondary)
        .accessibilityElement(children: .combine)
    }
}

struct WelcomeView: View {
    @ObservedObject var model: AppModel
    @ObservedObject var updater: AppUpdater
    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 24)
            DuoLink(link: .searching, tile: 64, mark: true).frame(width: 360)
            Text("Pair your Android phone").font(.system(size: 30, weight: .semibold)).tracking(-0.5).padding(.top, 40)
            Button { model.makeTicket() } label: { Text("Show Pairing Code").padding(.horizontal, 8) }
                .buttonStyle(.borderedProminent).controlSize(.extraLarge).keyboardShortcut(.defaultAction).padding(.top, 30)
            Link(destination: URL(string: "https://rohit-yadavv.github.io/dropduo/#download")!) {
                HStack(spacing: 3) { Text("Get DropDuo for Android"); Image(systemName: "arrow.up.right").font(.caption.weight(.semibold)) }
            }
            .font(.callout).padding(.top, 16)
            Spacer(minLength: 24)
            VStack(spacing: 12) {
                UpdateNotice(updater: updater).frame(maxWidth: 360)
                ReceiveStatus(model: model)
            }
        }
        .padding(32).frame(maxWidth: .infinity, maxHeight: .infinity)
        .navigationTitle("DropDuo")
    }
}

private extension View {
    /// Keep the title legible when media previews scroll behind the toolbar.
    @ViewBuilder func readableToolbar() -> some View {
        if #available(macOS 26, *) { toolbarBackgroundVisibility(.visible, for: .windowToolbar) } else { self }
    }
}
