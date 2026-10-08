// CI-only: ensure an update signature matches the public key embedded into the released app.
import CryptoKit
import Foundation

do {
    guard CommandLine.arguments.count == 4,
          let key = Data(base64Encoded: CommandLine.arguments[2]),
          let signature = Data(base64Encoded: CommandLine.arguments[3]) else { throw CocoaError(.fileReadCorruptFile) }
    let publicKey = try Curve25519.Signing.PublicKey(rawRepresentation: key)
    let data = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]), options: .mappedIfSafe)
    guard publicKey.isValidSignature(signature, for: data) else { throw CocoaError(.fileReadCorruptFile) }
} catch {
    fputs("Update signature does not match the configured public key.\n", stderr)
    exit(1)
}
