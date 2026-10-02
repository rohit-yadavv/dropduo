import SwiftUI
import AppKit
import UniformTypeIdentifiers
import CoreImage.CIFilterBuiltins
import DropDuoCore

@main struct DropDuoApp: App {
    @StateObject private var model = AppModel()
    var body: some Scene {
        WindowGroup("DropDuo", id: "main") { MainView(model: model).frame(minWidth: 760, minHeight: 580) }
            .defaultSize(width: 900, height: 660)
        MenuBarExtra {
            MenuContent(model: model)
        } label: { Image(nsImage: Brand.menuIcon).accessibilityLabel("DropDuo") }
    }
}
struct MenuContent: View {
    @ObservedObject var model: AppModel
    @Environment(\.openWindow) private var openWindow
    var body: some View {
        Text(model.online.isEmpty ? "No phone connected" : "Phone connected")
        Button("Open DropDuo") { openWindow(id: "main"); NSApp.activate(ignoringOtherApps: true) }
        Button("Send files…") { model.chooseFiles() }.disabled(model.online.isEmpty)
        Button("Open received files") { NSWorkspace.shared.open(model.inboxRoot) }
        Divider(); Button("Quit DropDuo") { NSApp.terminate(nil) }
    }
}
struct MainView: View {
    @ObservedObject var model: AppModel
    var body: some View {
        NavigationSplitView {
            VStack(alignment: .leading, spacing: 24) {
                HStack(spacing: 10) { Image("DropDuoMark", bundle: .module).resizable().frame(width: 36, height: 36).accessibilityHidden(true); Text("DropDuo").font(.title2.bold()) }
                List(selection: $model.route) {
                    Label("Share", systemImage: "paperplane").tag("Share")
                    Label("Devices", systemImage: "iphone.and.arrow.forward").tag("Devices")
                    Label("Recent", systemImage: "clock").tag("Recent")
                    Label("Settings", systemImage: "slider.horizontal.3").tag("Settings")
                }.listStyle(.sidebar)
                Text("Your devices.\nA little closer.").font(.callout).foregroundStyle(.secondary).padding(.bottom, 8)
            }.padding(16).navigationSplitViewColumnWidth(210)
        } detail: {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    HStack { VStack(alignment: .leading, spacing: 5) { Text(model.route).font(.largeTitle.bold()); Text(subtitle).foregroundStyle(.secondary) }; Spacer(); Label(model.online.isEmpty ? "Offline" : "Connected", systemImage: "circle.fill").font(.caption).foregroundStyle(model.online.isEmpty ? Color.secondary : Color.green) }
                    switch model.route {
                    case "Devices": devices
                    case "Recent": history
                    case "Settings": settings
                    default: sharing
                    }
                    Text(model.status).font(.caption).foregroundStyle(.secondary)
                }.padding(30)
            }.background(Color(nsColor: .windowBackgroundColor))
        }
        .alert("DropDuo", isPresented: Binding(get: { model.error != nil }, set: { if !$0 { model.error = nil } })) { Button("OK") { model.error = nil } } message: { Text(model.error ?? "") }
        .confirmationDialog("Forget this device?", isPresented: Binding(get: { model.confirmation != nil }, set: { if !$0 { model.confirmation = nil } })) {
            if let device = model.confirmation { Button("Forget \(device.name)", role: .destructive) { model.forget(device); model.confirmation = nil } }
        } message: { Text("It will need to pair again before sharing.") }
    }
    private var subtitle: String {
        switch model.route { case "Devices": return "Pair once. Keep sharing."; case "Recent": return "What moved between your devices."; case "Settings": return "A few controls. Nothing more."; default: return "Send something to your Android phone." }
    }
    private var sharing: some View {
        VStack(alignment: .leading, spacing: 20) {
            if model.devices.isEmpty {
                VStack(alignment: .leading, spacing: 12) {
                    Label("Start with your phone", systemImage: "iphone").font(.title3.bold())
                    Text("Install DropDuo on Android, then scan the code on your Mac. Both devices need the same local network.").foregroundStyle(.secondary)
                    Button("Pair a device") { model.route = "Devices"; model.makeTicket() }.buttonStyle(.borderedProminent)
                }.padding(24).frame(maxWidth: .infinity, alignment: .leading).background(.blue.opacity(0.07), in: RoundedRectangle(cornerRadius: 18))
            } else {
                Picker("Send to", selection: $model.selected) { ForEach(model.devices) { device in Text(device.name + (model.online.contains(device.id) ? " · Connected" : " · Offline")).tag(device.id) } }
                VStack(spacing: 12) {
                    Image(systemName: "doc.on.doc").font(.system(size: 42, weight: .light)).foregroundStyle(.blue)
                    Text("Drop files here").font(.title2.bold())
                    Text("Photos, videos, documents—original quality.").foregroundStyle(.secondary)
                    Button("Choose files…") { model.chooseFiles() }.buttonStyle(.borderedProminent).disabled(!model.online.contains(model.selected))
                }.frame(maxWidth: .infinity).padding(36).background(model.targeted ? .blue.opacity(0.12) : .blue.opacity(0.04), in: RoundedRectangle(cornerRadius: 20))
                .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(.blue.opacity(0.25), style: StrokeStyle(lineWidth: 1, dash: [6])))
                .onDrop(of: [UTType.fileURL], isTargeted: $model.targeted) { providers in
                    for provider in providers { _ = provider.loadObject(ofClass: URL.self) { url, _ in if let url { Task { @MainActor in model.send([url]) } } } }; return true
                }
                Text("Send a link or text").font(.headline)
                TextEditor(text: $model.text).font(.body).frame(height: 90).padding(8).background(.background, in: RoundedRectangle(cornerRadius: 10))
                HStack { Text("Shared deliberately. No clipboard monitoring.").font(.caption).foregroundStyle(.secondary); Spacer(); Button("Send text") { model.sendText() }.disabled(model.text.isEmpty || !model.online.contains(model.selected)) }
            }
            if let transfer = model.transfers.first { transferRow(transfer) }
        }
    }
    private var devices: some View {
        VStack(alignment: .leading, spacing: 18) {
            ForEach(model.devices) { device in
                HStack { Image(systemName: "iphone").font(.title); VStack(alignment: .leading) { Text(device.name).bold(); Text(model.online.contains(device.id) ? "Connected · Trusted" : "Offline · Trusted").font(.caption).foregroundStyle(.secondary) }; Spacer(); Button("Forget") { model.confirmation = device } }.padding(18).background(.quaternary.opacity(0.3), in: RoundedRectangle(cornerRadius: 14))
            }
            if let ticket = model.ticket, let code = try? ticket.code() {
                VStack(spacing: 14) {
                    if let image = qr(code) { Image(nsImage: image).interpolation(.none).resizable().frame(width: 230, height: 230).padding(12).background(.white, in: RoundedRectangle(cornerRadius: 12)).accessibilityLabel("Pairing QR code") }
                    Text("Scan with DropDuo on Android").font(.headline)
                    Text("Expires in 5 minutes. Keep this code private.").font(.caption).foregroundStyle(.secondary)
                    Picker("Network address", selection: Binding(get: { ticket.host }, set: { model.useAddress($0) })) { ForEach(model.localAddresses(), id: \.self) { Text($0).tag($0) } }.frame(maxWidth: 300)
                    Button("Copy pairing code") { NSPasteboard.general.clearContents(); NSPasteboard.general.setString(code, forType: .string) }
                }.frame(maxWidth: .infinity).padding(24).background(.blue.opacity(0.04), in: RoundedRectangle(cornerRadius: 18))
            }
            Button(model.ticket == nil ? "Pair another device" : "Generate a new code") { model.makeTicket() }.buttonStyle(.borderedProminent)
        }
    }
    private var history: some View {
        VStack(alignment: .leading, spacing: 12) {
            if model.transfers.isEmpty { ContentUnavailableView("Nothing shared yet", systemImage: "tray", description: Text("Your recent transfers will appear here.")) }
            ForEach(model.transfers) { transferRow($0) }
        }
    }
    private func transferRow(_ transfer: Transfer) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack { Image(systemName: transfer.text == nil ? "doc" : "text.bubble").foregroundStyle(.blue); VStack(alignment: .leading) { Text(transfer.name).lineLimit(1).bold(); Text("\(transfer.direction) · \(transfer.state)").font(.caption).foregroundStyle(.secondary) }; Spacer()
                if transfer.state == "Complete" {
                    if let text = transfer.text { Button("Copy") { NSPasteboard.general.clearContents(); NSPasteboard.general.setString(text, forType: .string) } }
                    else if let path = transfer.path { Button("Show") { NSWorkspace.shared.activateFileViewerSelecting([URL(fileURLWithPath: path)]) } }
                } else if transfer.state == "Interrupted", transfer.direction == "Sent", transfer.path != nil { Button("Retry") { model.retry(transfer) } }
                else if ["Preparing", "Sending", "Receiving"].contains(transfer.state) { Button("Cancel") { model.cancel(transfer) } }
            }
            if ["Sending", "Receiving"].contains(transfer.state) { ProgressView(value: transfer.progress) }
            if let text = transfer.text { Text(text).lineLimit(3).textSelection(.enabled).foregroundStyle(.secondary) }
            if let error = transfer.error { Text(error).font(.caption).foregroundStyle(.orange) }
        }.padding(16).background(.background, in: RoundedRectangle(cornerRadius: 12))
    }
    private var settings: some View {
        VStack(alignment: .leading, spacing: 22) {
            Toggle("Accept files and text from paired devices", isOn: $model.receiving).onChange(of: model.receiving) { _, _ in model.persistSettings() }
            Text("Only devices you approve can connect. Pausing receiving keeps pairing intact.").foregroundStyle(.secondary)
            Button("Open received files") { NSWorkspace.shared.open(model.inboxRoot) }
            Text("Files are stored in Downloads/DropDuo, separated by device. History keeps the latest 100 transfers; clearing it does not delete files.").font(.callout).foregroundStyle(.secondary)
            Button("Clear recent history") { model.clearHistory() }
            Divider()
            Text("DropDuo \(Bundle.main.object(forInfoDictionaryKey: "DropDuoVersion") as? String ?? "development")").bold()
            Text("Local network only. No accounts. No analytics. Mac sleep pauses availability.").foregroundStyle(.secondary)
        }.padding(24).background(.background, in: RoundedRectangle(cornerRadius: 16))
    }
    private func qr(_ value: String) -> NSImage? {
        let filter = CIFilter.qrCodeGenerator(); filter.message = Data(value.utf8)
        guard let output = filter.outputImage?.transformed(by: CGAffineTransform(scaleX: 8, y: 8)), let cg = CIContext().createCGImage(output, from: output.extent) else { return nil }
        return NSImage(cgImage: cg, size: NSSize(width: output.extent.width, height: output.extent.height))
    }
}
