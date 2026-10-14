import SwiftUI
import AppKit
import QuickLookThumbnailing
import UniformTypeIdentifiers

struct DeviceView: View {
    @ObservedObject var model: AppModel
    let device: Device
    @FocusState private var composing: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    private var online: Bool { model.isOnline(device) }
    /// Oldest first, so the newest transfer sits beside the composer.
    private var items: [Transfer] { model.transfers.filter { $0.peer == device.id }.reversed() }

    var body: some View {
        Group { if items.isEmpty { emptyState } else { timeline } }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .safeAreaInset(edge: .bottom, spacing: 0) {
                VStack(spacing: 8) {
                    if !online { offlineNotice.transition(.move(edge: .bottom).combined(with: .opacity)) }
                    composer
                }
                .padding(.horizontal, 20).padding(.top, 6).padding(.bottom, 16)
                .animation(reduceMotion ? nil : .easeOut(duration: 0.2), value: online)
            }
            .navigationTitle(device.name)
            .navigationSubtitle(online ? "Connected" : "Not reachable")
            .toolbar {
                ToolbarItemGroup {
                    Button { NSWorkspace.shared.open(model.inboxRoot) } label: { Label("Received Files", systemImage: "folder") }
                        .help("Open received files in Finder")
                    Menu {
                        Button("Forget Device…", role: .destructive) { model.confirmation = device }
                    } label: { Label("More", systemImage: "ellipsis") }
                        .menuIndicator(.hidden).help("More")
                }
            }
            .onDrop(of: [UTType.fileURL], isTargeted: $model.targeted) { providers in
                for provider in providers { _ = provider.loadObject(ofClass: URL.self) { url, _ in if let url { Task { @MainActor in model.send([url]) } } } }
                return true
            }
            .overlay { if model.targeted { dropOverlay.transition(.opacity.combined(with: .scale(scale: 0.98))) } }
            .animation(reduceMotion ? nil : .spring(response: 0.25, dampingFraction: 0.8), value: model.targeted)
    }

    private var timeline: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(Array(items.enumerated()), id: \.element.id) { index, transfer in
                        if index == 0 || !Calendar.current.isDate(items[index - 1].date, inSameDayAs: transfer.date) { DayHeader(date: transfer.date) }
                        TransferItem(model: model, transfer: transfer, device: device.name).id(transfer.id)
                    }
                }
                .padding(.horizontal, 24).padding(.vertical, 12)
            }
            .defaultScrollAnchor(.bottom)
            .task {
                await Task.yield()
                proxy.scrollTo(items.last?.id, anchor: .bottom)
            }
            .onChange(of: items.last?.id) { _, id in proxy.scrollTo(id, anchor: .bottom) }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 0) {
            DuoLink(link: online ? .on : .off, tile: 56).frame(width: 300)
            Text("Drop files here")
                .font(.title2.weight(.semibold)).multilineTextAlignment(.center).padding(.top, 32)
            Button("Choose Files…") { model.chooseFiles() }.controlSize(.large).padding(.top, 22)
        }
        .padding(40)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background {
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .strokeBorder(Color.secondary.opacity(0.28), style: StrokeStyle(lineWidth: 1.5, dash: [7, 7]))
                .padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 4)
        }
    }

    private var offlineNotice: some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Image(systemName: "wifi.slash").font(.system(size: 12, weight: .semibold)).foregroundStyle(.orange)
            Text("Open DropDuo on your phone and connect to the same Wi-Fi. Transfers send when it reconnects.")
                .font(.callout).foregroundStyle(.secondary)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14).padding(.vertical, 11)
        .background {
            let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
            shape.fill(reduceTransparency ? AnyShapeStyle(Brand.panelSurface) : AnyShapeStyle(.regularMaterial))
                .overlay(shape.fill(Color.orange.opacity(0.07))).overlay(shape.strokeBorder(Brand.hairline))
                .shadow(color: Brand.shadow, radius: 6, y: 2)
        }
    }

    private var composer: some View {
        HStack(alignment: .bottom, spacing: 8) {
            Button { model.chooseFiles() } label: {
                Image(systemName: "plus").font(.system(size: 14, weight: .semibold)).frame(width: 30, height: 30)
                    .background(Color.primary.opacity(0.07), in: Circle()).contentShape(Circle())
            }
            .buttonStyle(.plain).foregroundStyle(.secondary).help("Send files").accessibilityLabel("Send files")
            TextField("Link or note", text: $model.text, axis: .vertical)
                .textFieldStyle(.plain).lineLimit(1...6).focused($composing).padding(.vertical, 6)
                .accessibilityLabel("Link or note for \(device.name)")
                .onSubmit(send)
            Button(action: send) {
                Image(systemName: "arrow.up").font(.system(size: 13, weight: .bold)).foregroundStyle(.white).frame(width: 30, height: 30)
                    .background(canSend ? Brand.actionFill : Color.secondary.opacity(0.3), in: Circle()).contentShape(Circle())
            }
            .buttonStyle(.plain).disabled(!canSend).help("Send text").accessibilityLabel("Send text")
            .keyboardShortcut(.return, modifiers: .command)
        }
        .padding(6)
        .panel(cornerRadius: 21)
        .animation(.easeOut(duration: 0.15), value: canSend)
    }

    private var dropOverlay: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 20, style: .continuous).fill(Brand.cobalt.opacity(0.07))
            RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(Brand.cobalt.opacity(0.8), style: StrokeStyle(lineWidth: 2, dash: [10, 7]))
            VStack(spacing: 14) {
                Image(systemName: "arrow.down").font(.system(size: 22, weight: .bold)).foregroundStyle(.white)
                    .frame(width: 56, height: 56).background(Brand.cobalt, in: Circle()).shadow(color: Brand.cobalt.opacity(0.3), radius: 10, y: 4)
                Text(online ? "Drop to send to \(device.name)" : "Drop to send when \(device.name) is back").font(.title3.weight(.semibold))
            }
            .padding(.horizontal, 30).padding(.vertical, 24)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
        .padding(12).allowsHitTesting(false)
    }

    private var canSend: Bool { !model.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    private func send() { if canSend { model.sendText() } }
}

private struct DayHeader: View {
    let date: Date
    var body: some View {
        Text(label).font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            .padding(.horizontal, 10).padding(.vertical, 4)
            .background(Color.primary.opacity(0.05), in: Capsule())
            .padding(.top, 10).padding(.bottom, 2)
    }
    private var label: String {
        if Calendar.current.isDateInToday(date) { return "Today" }
        if Calendar.current.isDateInYesterday(date) { return "Yesterday" }
        return date.formatted(.dateTime.weekday(.wide).day().month(.wide))
    }
}

struct TransferItem: View {
    @ObservedObject var model: AppModel
    let transfer: Transfer
    let device: String
    // `State` values rather than `@State`: Command Line Tools on macOS 27 ship without the SwiftUI macro plugin that `@State` needs.
    private let hover = State(initialValue: false)
    private let copiedState = State(initialValue: false)
    private let thumbnailState = State<NSImage?>(initialValue: nil)
    private var hovering: Bool { hover.wrappedValue }
    private var copied: Bool { copiedState.wrappedValue }
    private var thumbnail: NSImage? { thumbnailState.wrappedValue }
    private var sent: Bool { transfer.direction == "Sent" }
    private var active: Bool { ["Preparing", "Sending", "Receiving"].contains(transfer.state) }
    private var waiting: Bool { transfer.state == "Interrupted" && transfer.autoRetry == true }
    private var failed: Bool { transfer.state == "Interrupted" && !waiting }
    private var complete: Bool { transfer.state == "Complete" }
    /// The file on this Mac, when it's still there.
    private var file: URL? { transfer.path.flatMap { FileManager.default.fileExists(atPath: $0) ? URL(fileURLWithPath: $0) : nil } }
    private var type: UTType? { UTType(filenameExtension: (transfer.name as NSString).pathExtension) }
    private var media: Bool { type.map { $0.conforms(to: .image) || $0.conforms(to: .movie) } ?? false }

    var body: some View {
        VStack(alignment: sent ? .trailing : .leading, spacing: 5) {
            if let text = transfer.text { textBubble(text) } else { fileCard }
            caption
        }
        .frame(maxWidth: .infinity, alignment: sent ? .trailing : .leading)
        .padding(sent ? .leading : .trailing, 72)
        .onHover { hover.wrappedValue = $0 }
    }

    // MARK: Text

    private func textBubble(_ text: String) -> some View {
        let shape = UnevenRoundedRectangle(topLeadingRadius: 18, bottomLeadingRadius: sent ? 18 : 6, bottomTrailingRadius: sent ? 6 : 18, topTrailingRadius: 18, style: .continuous)
        return Group {
            if let url = webLink(text) { linkLabel(url) } else { Text(linked(text)).textSelection(.enabled) }
        }
        .tint(sent ? .white : Brand.cobalt)
        .foregroundStyle(sent ? Color.white : Color.primary)
        .padding(.horizontal, 14).padding(.vertical, 9)
        .background(sent ? AnyShapeStyle(Brand.actionFill) : AnyShapeStyle(Brand.surface), in: shape)
        .overlay(shape.strokeBorder(sent ? Color.clear : Brand.hairline))
        .shadow(color: Brand.shadow, radius: 2, y: 1)
        .frame(maxWidth: 460, alignment: sent ? .trailing : .leading)
        .contextMenu {
            Button("Copy") { copy(text) }
            if let url = webLink(text) { Button("Open Link") { NSWorkspace.shared.open(url) } }
        }
    }

    private func linkLabel(_ url: URL) -> some View {
        Button { NSWorkspace.shared.open(url) } label: {
            HStack(spacing: 10) {
                Image(systemName: "link").font(.system(size: 12, weight: .semibold)).frame(width: 30, height: 30)
                    .foregroundStyle(sent ? Color.white : Brand.cobalt)
                    .background(sent ? Color.white.opacity(0.18) : Brand.cobalt.opacity(0.12), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                VStack(alignment: .leading, spacing: 1) {
                    Text(url.host().map { $0.hasPrefix("www.") ? String($0.dropFirst(4)) : $0 } ?? "Link").font(.body.weight(.semibold)).lineLimit(1)
                    Text(url.absoluteString).font(.caption).lineLimit(1).truncationMode(.middle).opacity(0.9)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain).help(url.absoluteString)
    }

    // MARK: Files

    private var fileCard: some View {
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        return VStack(alignment: .leading, spacing: 0) {
            if media, file != nil {
                // Reserve the preview's space before Quick Look finishes so the timeline doesn't jump.
                Color.clear.aspectRatio(1.5, contentMode: .fit)
                    .overlay {
                        if let thumbnail { preview(thumbnail) }
                        else { Image(systemName: type?.conforms(to: .movie) == true ? "video" : "photo").font(.largeTitle).foregroundStyle(.secondary) }
                    }
                    .clipped().accessibilityHidden(true)
            }
            HStack(spacing: 12) {
                if !media || file == nil {
                    Image(nsImage: thumbnail ?? icon).resizable().aspectRatio(contentMode: .fit).frame(width: 36, height: 36)
                        .clipShape(RoundedRectangle(cornerRadius: thumbnail == nil ? 0 : 6, style: .continuous)).accessibilityHidden(true)
                }
                VStack(alignment: .leading, spacing: 3) {
                    Text(transfer.name).font(.body.weight(.medium)).lineLimit(1).truncationMode(.middle)
                    if active { ProgressView(value: transfer.progress).controlSize(.small).tint(Brand.cobalt) }
                    else if let detail { Text(detail).font(.caption).foregroundStyle(.secondary).lineLimit(1) }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                actions
            }
            .padding(12)
        }
        .frame(maxWidth: 300)
        .background(Brand.surface)
        .clipShape(shape)
        .overlay(shape.strokeBorder(hovering && file != nil && complete ? Brand.cobalt.opacity(0.45) : Brand.hairline))
        .shadow(color: Brand.shadow, radius: 3, y: 1)
        .opacity(waiting ? 0.75 : 1)
        .contentShape(shape)
        .onTapGesture(count: 2) { if complete, let file { NSWorkspace.shared.open(file) } }
        .contextMenu {
            if complete, let file {
                Button("Open") { NSWorkspace.shared.open(file) }
                Button("Show in Finder") { NSWorkspace.shared.activateFileViewerSelecting([file]) }
            }
        }
        .task(id: "\(transfer.path ?? "")|\(transfer.state)") {
            guard let path = transfer.path, complete || sent else { return }
            thumbnailState.wrappedValue = await Thumbnails.load(path, side: media ? 300 : 36)
        }
        .animation(.easeOut(duration: 0.15), value: hovering)
    }

    private func preview(_ image: NSImage) -> some View {
        Image(nsImage: image).resizable().aspectRatio(contentMode: .fill)
            .overlay {
                if type?.conforms(to: .movie) == true {
                    Image(systemName: "play.fill").font(.system(size: 15)).foregroundStyle(.white)
                        .frame(width: 42, height: 42).background(.black.opacity(0.4), in: Circle())
                }
            }
            .accessibilityHidden(true)
    }

    @ViewBuilder private var actions: some View {
        if complete, let file {
            Button { NSWorkspace.shared.activateFileViewerSelecting([file]) } label: {
                Image(systemName: "magnifyingglass").font(.system(size: 12, weight: .semibold)).frame(width: 26, height: 26)
                    .background(Color.primary.opacity(hovering ? 0.1 : 0.06), in: Circle())
            }
            .buttonStyle(.plain).foregroundStyle(.secondary).help("Show in Finder").accessibilityLabel("Show in Finder")
        } else if transfer.state == "Interrupted", sent, transfer.path != nil {
            Button("Retry") { model.retry(transfer) }.controlSize(.small)
            if waiting { stopButton(help: "Don't send") }
        } else if active {
            stopButton(help: "Cancel")
        }
    }

    private func stopButton(help: String) -> some View {
        Button { model.cancel(transfer) } label: { Image(systemName: "xmark.circle.fill").font(.system(size: 16)).symbolRenderingMode(.hierarchical) }
            .buttonStyle(.plain).foregroundStyle(.secondary).help(help).accessibilityLabel(help)
    }

    /// Kind and size, like "JPEG image, 2.4 MB".
    private var detail: String? {
        let size = file.flatMap { (try? $0.resourceValues(forKeys: [.fileSizeKey]))?.fileSize }.map { ByteCountFormatter.string(fromByteCount: Int64($0), countStyle: .file) }
        let parts = [type?.localizedDescription.map { $0.prefix(1).uppercased() + $0.dropFirst() }, size].compactMap { $0 }
        return parts.isEmpty ? nil : parts.joined(separator: ", ")
    }

    private var icon: NSImage {
        if let file { return NSWorkspace.shared.icon(forFile: file.path) }
        return NSWorkspace.shared.icon(for: type ?? .data)
    }

    // MARK: Caption

    private var caption: some View {
        HStack(spacing: 5) {
            if let symbol { Image(systemName: symbol).font(.caption2.weight(.bold)) }
            Text(status).monospacedDigit()
            if transfer.text != nil {
                if complete {
                    Button(copied ? "Copied" : "Copy") { copy(transfer.text ?? "") }.buttonStyle(.link)
                        .disabled(copied)
                } else if waiting {
                    Button("Don't Send") { model.cancel(transfer) }.buttonStyle(.link)
                } else if failed, sent {
                    Button("Retry") { model.retry(transfer) }.buttonStyle(.link)
                }
            }
        }
        .font(.caption).foregroundStyle(failed ? AnyShapeStyle(Color.orange) : AnyShapeStyle(.secondary))
        .padding(.horizontal, 6)
        .animation(.easeOut(duration: 0.12), value: hovering)
    }

    private var symbol: String? {
        if waiting { return "clock" }
        if failed { return "exclamationmark.circle" }
        return nil
    }

    private var status: String {
        let time = transfer.date.formatted(date: .omitted, time: .shortened)
        switch transfer.state {
        case "Preparing": return "Preparing…"
        case "Sending", "Receiving": return "\(transfer.state) \(Int(transfer.progress * 100))%"
        case "Complete": return "\(sent ? "Sent" : transfer.text == nil ? "In Downloads" : "Received") · \(time)"
        case "Cancelled": return "Cancelled · \(time)"
        case "Interrupted" where waiting: return transfer.error ?? "Sends when \(device) is back"
        case "Interrupted": return transfer.error ?? "Not sent"
        default: return transfer.state
        }
    }

    // MARK: Helpers

    /// A shared text counts as a link card only when it is a single web address.
    private func webLink(_ text: String) -> URL? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.contains(where: \.isWhitespace), let url = URL(string: trimmed), let scheme = url.scheme?.lowercased(),
              ["http", "https"].contains(scheme), url.host() != nil else { return nil }
        return url
    }

    /// Makes web addresses inside a note clickable.
    private func linked(_ text: String) -> AttributedString {
        var value = AttributedString(text)
        guard let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue) else { return value }
        for match in detector.matches(in: text, range: NSRange(text.startIndex..., in: text)) {
            guard let url = match.url, ["http", "https"].contains(url.scheme?.lowercased() ?? ""),
                  let range = Range(match.range, in: text), let target = Range(range, in: value) else { continue }
            value[target].link = url
            value[target].underlineStyle = .single
        }
        return value
    }

    private func copy(_ text: String) {
        NSPasteboard.general.clearContents(); NSPasteboard.general.setString(text, forType: .string)
        copiedState.wrappedValue = true
        Task { try? await Task.sleep(for: .seconds(1.5)); copiedState.wrappedValue = false }
    }
}

/// Quick Look thumbnails, cached for the session.
@MainActor private enum Thumbnails {
    private static let cache = NSCache<NSString, NSImage>()
    static func load(_ path: String, side: CGFloat) async -> NSImage? {
        let key = "\(Int(side))|\(path)" as NSString
        if let hit = cache.object(forKey: key) { return hit }
        guard FileManager.default.fileExists(atPath: path) else { return nil }
        let request = QLThumbnailGenerator.Request(fileAt: URL(fileURLWithPath: path), size: CGSize(width: side, height: side),
            scale: NSScreen.main?.backingScaleFactor ?? 2, representationTypes: .thumbnail)
        guard let image = try? await QLThumbnailGenerator.shared.generateBestRepresentation(for: request).nsImage else { return nil }
        cache.setObject(image, forKey: key)
        return image
    }
}

extension View {
    /// A floating control panel: Liquid Glass on macOS 26 and later, a material card before that.
    func panel(cornerRadius: CGFloat) -> some View { modifier(FloatingPanel(cornerRadius: cornerRadius)) }
}

private struct FloatingPanel: ViewModifier {
    let cornerRadius: CGFloat
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    @ViewBuilder func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        if reduceTransparency {
            content.background(Brand.panelSurface, in: shape).overlay(shape.strokeBorder(Brand.hairline))
        } else {
            // Older toolchains don't declare glassEffect, even inside a runtime availability check.
            #if compiler(>=6.2)
            if #available(macOS 26, *) {
                content.background(Brand.panelSurface.opacity(0.6), in: shape).glassEffect(.regular, in: shape)
                    .overlay(shape.strokeBorder(Brand.hairline))
            } else {
                materialPanel(content, in: shape)
            }
            #else
            materialPanel(content, in: shape)
            #endif
        }
    }

    private func materialPanel(_ content: Content, in shape: RoundedRectangle) -> some View {
        content.background(.regularMaterial, in: shape).overlay(shape.strokeBorder(Brand.hairline)).shadow(color: Brand.shadow, radius: 8, y: 2)
    }
}
