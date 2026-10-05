import SwiftUI
import AppKit
import CoreImage.CIFilterBuiltins
import DropDuoCore

struct PairingView: View {
    @ObservedObject var model: AppModel

    var body: some View {
        if let ticket = model.ticket, let code = try? ticket.code() {
            TimelineView(.periodic(from: .now, by: 1)) { context in
                let remaining = max(0, Int(ticket.expires) - Int(context.date.timeIntervalSince1970))
                content(ticket: ticket, code: code, remaining: remaining)
            }
        }
    }

    private func content(ticket: Ticket, code: String, remaining: Int) -> some View {
        VStack(spacing: 22) {
            VStack(spacing: 6) {
                Text("Pair with DropDuo on Android").font(.title2.weight(.semibold))
                Text("In the Android app, tap **Scan pairing code** and point it here.").foregroundStyle(.secondary)
            }
            ZStack {
                if let image = qr(code) {
                    Image(nsImage: image).interpolation(.none).resizable().frame(width: 220, height: 220)
                        .blur(radius: remaining == 0 ? 8 : 0).opacity(remaining == 0 ? 0.35 : 1)
                        .accessibilityLabel("Pairing QR code")
                }
                if remaining == 0 {
                    VStack(spacing: 10) {
                        Text("Code expired").font(.headline).foregroundStyle(.black)
                        Button("New Code") { model.makeTicket() }.buttonStyle(.borderedProminent)
                    }
                }
            }
            .padding(16).background(.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .shadow(color: .black.opacity(0.1), radius: 12, y: 4)

            Label(remaining == 0 ? "Expired" : String(format: "Expires in %d:%02d", remaining / 60, remaining % 60), systemImage: "timer")
                .font(.callout.monospacedDigit()).foregroundStyle(remaining < 60 ? .orange : .secondary)

            VStack(alignment: .leading, spacing: 10) {
                Label("You'll approve the phone on this Mac before it can send anything.", systemImage: "checkmark.shield")
                Label("Keep this code private. Anyone who scans it can ask to pair.", systemImage: "lock")
            }
            .font(.callout).foregroundStyle(.secondary).frame(maxWidth: 340, alignment: .leading)

            let addresses = model.localAddresses()
            if addresses.count > 1 {
                Picker("Network", selection: Binding(get: { ticket.host }, set: { model.useAddress($0) })) {
                    ForEach(addresses, id: \.self) { Text($0).tag($0) }
                }.frame(maxWidth: 280).help("Pick the address on the same network as your phone")
            }

            HStack {
                Button("Copy Code") { NSPasteboard.general.clearContents(); NSPasteboard.general.setString(code, forType: .string) }.disabled(remaining == 0)
                Spacer()
                Button("Cancel") { model.cancelPairing() }.keyboardShortcut(.cancelAction)
            }
        }
        .padding(28).frame(width: 440)
    }

    private func qr(_ value: String) -> NSImage? {
        let filter = CIFilter.qrCodeGenerator(); filter.message = Data(value.utf8)
        guard let output = filter.outputImage?.transformed(by: CGAffineTransform(scaleX: 8, y: 8)), let cg = CIContext().createCGImage(output, from: output.extent) else { return nil }
        return NSImage(cgImage: cg, size: NSSize(width: output.extent.width, height: output.extent.height))
    }
}
