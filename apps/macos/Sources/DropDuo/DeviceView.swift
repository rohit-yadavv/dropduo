import SwiftUI
import AppKit
import UniformTypeIdentifiers

struct DeviceView: View {
    @ObservedObject var model: AppModel
    let device: Device
    @FocusState private var composing: Bool
    private var online: Bool { model.isOnline(device) }
    /// Oldest first, so the newest transfer sits beside the composer.
    private var items: [Transfer] { model.transfers.filter { $0.peer == device.id }.reversed() }

    var body: some View {
        VStack(spacing: 0) {
            if items.isEmpty { emptyState } else { timeline }
            if !online { offlineBanner }
            composer
        }
        .navigationTitle(device.name)
        .navigationSubtitle(online ? "Connected" : "Not reachable")
        .toolbar {
            ToolbarItemGroup {
                Button { NSWorkspace.shared.open(model.inboxRoot) } label: { Label("Received Files", systemImage: "folder") }
                    .help("Open received files in Finder")
                Menu {
                    Button("Forget Device…", role: .destructive) { model.confirmation = device }
                } label: { Label("More", systemImage: "ellipsis.circle") }
            }
        }
        .onDrop(of: [UTType.fileURL], isTargeted: $model.targeted) { providers in
            guard online else { return false }
            for provider in providers { _ = provider.loadObject(ofClass: URL.self) { url, _ in if let url { Task { @MainActor in model.send([url]) } } } }
            return true
        }
        .overlay { if model.targeted { dropOverlay } }
        .animation(.easeOut(duration: 0.15), value: model.targeted)
    }

    private var timeline: some View {
        ScrollView {
            LazyVStack(spacing: 14) {
                ForEach(Array(items.enumerated()), id: \.element.id) { index, transfer in
                    if index == 0 || !Calendar.current.isDate(items[index - 1].date, inSameDayAs: transfer.date) {
                        Text(transfer.date, format: .dateTime.weekday(.wide).day().month(.wide))
                            .font(.caption.weight(.medium)).foregroundStyle(.secondary).padding(.top, 6)
                    }
                    TransferBubble(model: model, transfer: transfer)
                }
            }
            .padding(.horizontal, 24).padding(.vertical, 20)
        }
        .defaultScrollAnchor(.bottom)
    }

    private var emptyState: some View {
        VStack(spacing: 14) {
            Image(systemName: "arrow.down.doc").font(.system(size: 40, weight: .light)).foregroundStyle(Brand.cobalt)
            Text("Nothing shared yet").font(.title2.weight(.semibold))
            Text("Drop files anywhere in this window, or type a link or note below.\nThings \(device.name) sends you show up here too.")
                .multilineTextAlignment(.center).foregroundStyle(.secondary)
        }
        .padding(40).frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var offlineBanner: some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Image(systemName: "wifi.exclamationmark").foregroundStyle(.orange)
            VStack(alignment: .leading, spacing: 2) {
                Text("\(device.name) isn't reachable").font(.callout.weight(.semibold))
                Text("Open DropDuo on the phone and make sure both devices are on the same Wi-Fi. It reconnects automatically.")
                    .font(.callout).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
        }
        .padding(12).background(.orange.opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        .padding(.horizontal, 16).padding(.vertical, 8)
    }

    private var composer: some View {
        HStack(alignment: .bottom, spacing: 10) {
            Button { model.chooseFiles() } label: { Image(systemName: "paperclip").font(.system(size: 16, weight: .medium)).frame(width: 32, height: 32) }
                .buttonStyle(.borderless).help("Send files").accessibilityLabel("Send files").disabled(!online)
            TextField(online ? "Send a link or text" : "Waiting for \(device.name)…", text: $model.text, axis: .vertical)
                .textFieldStyle(.plain).lineLimit(1...6).focused($composing)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(Color(nsColor: .controlBackgroundColor), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(.separator))
                .onSubmit(send)
            Button(action: send) { Image(systemName: "arrow.up.circle.fill").font(.system(size: 26)).symbolRenderingMode(.hierarchical) }
                .buttonStyle(.borderless).foregroundStyle(canSend ? Brand.cobalt : Color.secondary)
                .disabled(!canSend).help("Send text").accessibilityLabel("Send text")
                .keyboardShortcut(.return, modifiers: .command)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .background(.bar)
        .overlay(alignment: .top) { Divider() }
    }

    private var dropOverlay: some View {
        RoundedRectangle(cornerRadius: 16, style: .continuous)
            .strokeBorder(online ? Brand.cobalt : Color.secondary, style: StrokeStyle(lineWidth: 2, dash: [8, 6]))
            .background((online ? Brand.cobalt : Color.secondary).opacity(0.08), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay {
                Label(online ? "Drop to send to \(device.name)" : "\(device.name) isn't reachable", systemImage: online ? "arrow.down.circle.fill" : "wifi.slash")
                    .font(.title3.weight(.semibold)).padding(.horizontal, 18).padding(.vertical, 10).background(.regularMaterial, in: Capsule())
            }
            .padding(12).allowsHitTesting(false)
    }

    private var canSend: Bool { online && !model.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    private func send() { if canSend { model.sendText() } }
}

struct TransferBubble: View {
    @ObservedObject var model: AppModel
    let transfer: Transfer
    private var sent: Bool { transfer.direction == "Sent" }
    private var active: Bool { ["Preparing", "Sending", "Receiving"].contains(transfer.state) }

    var body: some View {
        HStack {
            if sent { Spacer(minLength: 80) }
            VStack(alignment: sent ? .trailing : .leading, spacing: 4) {
                if let text = transfer.text { textBubble(text) } else { fileBubble }
                caption
            }
            if !sent { Spacer(minLength: 80) }
        }
    }

    private func textBubble(_ text: String) -> some View {
        Text(link(text) ?? AttributedString(text))
            .textSelection(.enabled).tint(sent ? .white : Brand.cobalt)
            .padding(.horizontal, 14).padding(.vertical, 9)
            .foregroundStyle(sent ? Color.white : Color.primary)
            .background(sent ? AnyShapeStyle(Brand.cobalt) : AnyShapeStyle(.quaternary), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .contextMenu { Button("Copy") { copy(text) } }
    }

    private var fileBubble: some View {
        HStack(spacing: 12) {
            Image(nsImage: icon).resizable().frame(width: 36, height: 36).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(transfer.name).lineLimit(1).truncationMode(.middle).font(.body.weight(.medium))
                if active { ProgressView(value: transfer.progress).controlSize(.small) }
                if let error = transfer.error, transfer.state != "Complete" { Text(error).font(.caption).foregroundStyle(.orange).lineLimit(2) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            actions
        }
        .padding(12).frame(width: 320)
        .background(Color(nsColor: .controlBackgroundColor), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(sent ? Brand.cobalt.opacity(0.35) : Color.primary.opacity(0.08)))
        .contentShape(Rectangle())
        .onTapGesture(count: 2) { if transfer.state == "Complete", let path = transfer.path { NSWorkspace.shared.open(URL(fileURLWithPath: path)) } }
        .contextMenu {
            if transfer.state == "Complete", let path = transfer.path {
                Button("Open") { NSWorkspace.shared.open(URL(fileURLWithPath: path)) }
                Button("Show in Finder") { reveal(path) }
            }
        }
    }

    @ViewBuilder private var actions: some View {
        if transfer.state == "Complete", let path = transfer.path {
            Button { reveal(path) } label: { Image(systemName: "magnifyingglass") }.buttonStyle(.borderless).help("Show in Finder").accessibilityLabel("Show in Finder")
        } else if transfer.state == "Interrupted", sent, transfer.path != nil {
            Button("Retry") { model.retry(transfer) }.controlSize(.small)
        } else if active {
            Button { model.cancel(transfer) } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary) }
                .buttonStyle(.borderless).help("Cancel").accessibilityLabel("Cancel transfer")
        }
    }

    private var caption: some View {
        HStack(spacing: 4) {
            Text(status)
            Text("·")
            Text(transfer.date, format: .dateTime.hour().minute())
            if transfer.text != nil, transfer.state == "Complete" {
                Button("Copy") { copy(transfer.text ?? "") }.buttonStyle(.link).font(.caption)
            }
        }
        .font(.caption).foregroundStyle(.secondary).padding(.horizontal, 4)
    }

    private var status: String {
        switch transfer.state {
        case "Preparing": return "Preparing…"
        case "Sending", "Receiving": return "\(transfer.state) · \(Int(transfer.progress * 100))%"
        case "Complete": return sent ? "Sent" : (transfer.text == nil ? "Saved to Downloads" : "Received")
        default: return transfer.state
        }
    }

    private var icon: NSImage {
        if let path = transfer.path, FileManager.default.fileExists(atPath: path) { return NSWorkspace.shared.icon(forFile: path) }
        let type = UTType(filenameExtension: (transfer.name as NSString).pathExtension) ?? .data
        return NSWorkspace.shared.icon(for: type)
    }

    private func link(_ text: String) -> AttributedString? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = URL(string: trimmed), let scheme = url.scheme?.lowercased(), ["http", "https"].contains(scheme) else { return nil }
        var value = AttributedString(trimmed); value.link = url; value.underlineStyle = .single; return value
    }
    private func copy(_ text: String) { NSPasteboard.general.clearContents(); NSPasteboard.general.setString(text, forType: .string) }
    private func reveal(_ path: String) { NSWorkspace.shared.activateFileViewerSelecting([URL(fileURLWithPath: path)]) }
}
