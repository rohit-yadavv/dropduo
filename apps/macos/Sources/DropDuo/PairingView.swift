import SwiftUI
import AppKit
import CoreImage.CIFilterBuiltins
import DropDuoCore

struct PairingView: View {
    @ObservedObject var model: AppModel
    // `State` values rather than `@State`: Command Line Tools on macOS 27 ship without the SwiftUI macro plugin that `@State` needs.
    private let copiedState = State(initialValue: false)
    private var copied: Bool { copiedState.wrappedValue }

    var body: some View {
        if let ticket = model.ticket, let code = try? ticket.code() {
            TimelineView(.periodic(from: .now, by: 1)) { context in
                let remaining = max(0, Int(ticket.expires) - Int(context.date.timeIntervalSince1970))
                content(ticket: ticket, code: code, remaining: remaining)
            }
        }
    }

    private func content(ticket: Ticket, code: String, remaining: Int) -> some View {
        let expired = remaining == 0
        return VStack(spacing: 0) {
            Text("Scan with your phone").font(.title2.weight(.semibold))

            VStack(spacing: 14) {
                ZStack {
                    if let image = qr(code) {
                        Image(nsImage: image).interpolation(.none).resizable().frame(width: 216, height: 216)
                            .blur(radius: expired ? 8 : 0).opacity(expired ? 0.3 : 1)
                            .accessibilityLabel("Pairing QR code")
                    }
                    if expired {
                        VStack(spacing: 10) {
                            Text("This code expired").font(.headline).foregroundStyle(Color(white: 0.12))
                            Button("New Code") { model.makeTicket() }.buttonStyle(.borderedProminent).keyboardShortcut(.defaultAction)
                        }
                    }
                }
                expiry(remaining)
            }
            .padding(18).padding(.bottom, -2)
            .background(.white, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(.black.opacity(0.06)))
            .shadow(color: .black.opacity(0.1), radius: 16, y: 6)
            .environment(\.colorScheme, .light)
            .padding(.top, 22)

            VStack(alignment: .leading, spacing: 10) {
                Label("Approve the pairing request on this Mac.", systemImage: "checkmark.shield")
                Label("Only share this code with your own devices.", systemImage: "lock")
            }
            .labelStyle(NoteLabelStyle())
            .font(.callout).foregroundStyle(.secondary).frame(maxWidth: 340, alignment: .leading).padding(.top, 24)

            let addresses = model.localAddresses()
            if addresses.count > 1 {
                Picker("Network", selection: Binding(get: { ticket.host }, set: { model.useAddress($0) })) {
                    ForEach(addresses, id: \.self) { Text($0).tag($0) }
                }
                .frame(maxWidth: 300).padding(.top, 18).help("Pick the address on the same network as your phone")
            }

            HStack {
                Button { copy(code) } label: { Label(copied ? "Copied" : "Copy Code", systemImage: copied ? "checkmark" : "doc.on.doc") }
                    .disabled(expired).help("For typing in with Enter code instead on the phone")
                Spacer()
                Button("Cancel") { model.cancelPairing() }.keyboardShortcut(.cancelAction)
            }
            .padding(.top, 26)
        }
        .padding(28).frame(width: 440)
    }

    /// A thin bar under the code that drains over its five minutes.
    private func expiry(_ remaining: Int) -> some View {
        let fraction = Double(remaining) / 300
        let urgent = remaining < 60
        return VStack(spacing: 7) {
            Capsule().fill(Color.black.opacity(0.08)).frame(width: 216, height: 4)
                .overlay(alignment: .leading) {
                    Capsule().fill(urgent ? Color.orange : Brand.cobalt).frame(width: 216 * min(max(fraction, 0), 1), height: 4)
                        .animation(.linear(duration: 1), value: remaining)
                }
            Text(remaining == 0 ? "Expired" : String(format: "Expires in %d:%02d", remaining / 60, remaining % 60))
                .font(.caption.monospacedDigit()).foregroundStyle(urgent ? Color.orange : Color(white: 0.45))
        }
        .accessibilityElement(children: .combine)
    }

    private func copy(_ code: String) {
        NSPasteboard.general.clearContents(); NSPasteboard.general.setString(code, forType: .string)
        copiedState.wrappedValue = true
        Task { try? await Task.sleep(for: .seconds(1.5)); copiedState.wrappedValue = false }
    }

    /// Ink modules rather than pure black; still high contrast for phone cameras.
    private func qr(_ value: String) -> NSImage? {
        let filter = CIFilter.qrCodeGenerator(); filter.message = Data(value.utf8)
        let ink = CIFilter.falseColor(); ink.inputImage = filter.outputImage
        ink.color0 = CIColor(red: 0x20 / 255, green: 0x21 / 255, blue: 0x24 / 255); ink.color1 = CIColor(red: 1, green: 1, blue: 1)
        guard let output = ink.outputImage?.transformed(by: CGAffineTransform(scaleX: 8, y: 8)), let cg = CIContext().createCGImage(output, from: output.extent) else { return nil }
        return NSImage(cgImage: cg, size: NSSize(width: output.extent.width, height: output.extent.height))
    }
}

private struct NoteLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            configuration.icon.frame(width: 16)
            configuration.title.fixedSize(horizontal: false, vertical: true)
        }
    }
}
