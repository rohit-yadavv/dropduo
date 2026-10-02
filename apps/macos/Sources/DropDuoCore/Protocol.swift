import Foundation
import CryptoKit

public enum PortError: Error, LocalizedError {
    case invalid(String)
    public var errorDescription: String? { if case let .invalid(message) = self { return message }; return "Connection failed" }
}
public enum Wire {
    public static let version = 1
    public static let maxFrame = 400_000
    public static let chunkSize = 196_608
    public static let maxFile: Int64 = 32 * 1024 * 1024 * 1024
    public static func random(_ count: Int) -> Data {
        var bytes = [UInt8](repeating: 0, count: count)
        precondition(SecRandomCopyBytes(kSecRandomDefault, count, &bytes) == errSecSuccess)
        return Data(bytes)
    }
    public static func hmac(_ key: Data, _ text: String) -> String {
        Data(HMAC<SHA256>.authenticationCode(for: Data(text.utf8), using: SymmetricKey(data: key))).base64EncodedString()
    }
    public static func verify(_ proof: String, key: Data, text: String) -> Bool {
        guard let bytes = Data(base64Encoded: proof) else { return false }
        return HMAC<SHA256>.isValidAuthenticationCode(bytes, authenticating: Data(text.utf8), using: SymmetricKey(data: key))
    }
    public static func derive(secret: Data, client: Data, server: Data, direction: String) -> Data {
        let key = HKDF<SHA256>.deriveKey(inputKeyMaterial: SymmetricKey(data: secret), salt: client + server,
            info: Data("dropduo/1/\(direction)".utf8), outputByteCount: 32)
        return key.withUnsafeBytes { Data($0) }
    }
    public static func hash(_ file: URL) throws -> String {
        let handle = try FileHandle(forReadingFrom: file); defer { try? handle.close() }
        var hash = SHA256()
        while let data = try handle.read(upToCount: chunkSize), !data.isEmpty { hash.update(data: data) }
        return hash.finalize().map { String(format: "%02x", $0) }.joined()
    }
    public static func integer(_ value: UInt64) -> Data { var v = value.bigEndian; return Data(bytes: &v, count: 8) }
}

public struct Ticket: Codable, Equatable {
    public var version: Int = 1
    public var pairID: String
    public var host: String
    public var port: Int
    public var secret: String
    public var name: String
    public var discoveryName: String?
    public var expires: Int64
    public init(pairID: String, host: String, port: Int, secret: String, name: String, expires: Int64) {
        self.pairID = pairID; self.host = host; self.port = port; self.secret = secret; self.name = name; self.expires = expires
    }
    public func validate() throws {
        guard version == 1, UUID(uuidString: pairID) != nil, !host.isEmpty, (1...65535).contains(port),
              Data(base64Encoded: secret)?.count == 32, name.utf8.count <= 128 else { throw PortError.invalid("Invalid pairing code") }
    }
    public static let codePrefix = "dropduo://pair/"
    public func code() throws -> String { Self.codePrefix + (try JSONEncoder().encode(self)).base64EncodedString() }
    public static func parse(_ code: String) throws -> Ticket {
        guard code.hasPrefix(codePrefix), code.count < 4096, let data = Data(base64Encoded: String(code.dropFirst(codePrefix.count))) else { throw PortError.invalid("Invalid pairing code") }
        let ticket = try JSONDecoder().decode(Ticket.self, from: data); try ticket.validate(); return ticket
    }
}
public struct Hello: Codable {
    public var version: Int = 1
    public var pairID: String
    public var name: String
    public var nonce: String
    public var proof: String
    public init(pairID: String, name: String, nonce: String, proof: String) { self.pairID = pairID; self.name = name; self.nonce = nonce; self.proof = proof }
    public var transcript: String { "dropduo/1/hello|\(pairID)|\(nonce)|\(name)" }
}
public struct Welcome: Codable {
    public var version: Int = 1
    public var nonce: String
    public var proof: String
    public init(nonce: String, proof: String) { self.nonce = nonce; self.proof = proof }
}
public struct Message: Codable {
    public var type: String
    public var id: String?
    public var name: String?
    public var size: Int64?
    public var sha256: String?
    public var offset: Int64?
    public var data: String?
    public var text: String?
    public var error: String?
    public init(_ type: String, id: String? = nil, name: String? = nil, size: Int64? = nil, sha256: String? = nil,
                offset: Int64? = nil, data: String? = nil, text: String? = nil, error: String? = nil) {
        self.type = type; self.id = id; self.name = name; self.size = size; self.sha256 = sha256
        self.offset = offset; self.data = data; self.text = text; self.error = error
    }
}
public struct FrameCipher {
    private let key: SymmetricKey
    private var sequence: UInt64 = 0
    public init(key: Data) { self.key = SymmetricKey(data: key) }
    public mutating func seal(_ plain: Data, nonce: Data? = nil) throws -> Data {
        guard plain.count <= Wire.maxFrame - 36, sequence < UInt64.max else { throw PortError.invalid("Frame too large") }
        let aad = Wire.integer(sequence)
        let box = try AES.GCM.seal(plain, using: key, nonce: nonce.map { try AES.GCM.Nonce(data: $0) }, authenticating: aad)
        sequence += 1
        return aad + box.combined!
    }
    public mutating func open(_ frame: Data) throws -> Data {
        guard frame.count >= 36, frame.count <= Wire.maxFrame, frame.prefix(8) == Wire.integer(sequence) else { throw PortError.invalid("Invalid frame sequence or length") }
        let plain = try AES.GCM.open(AES.GCM.SealedBox(combined: frame.dropFirst(8)), using: key, authenticating: frame.prefix(8))
        sequence += 1; return plain
    }
}
