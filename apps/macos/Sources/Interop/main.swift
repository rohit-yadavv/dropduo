import Foundation
import NearportCore
let secret = Data((0..<32).map(UInt8.init)), client = Data(repeating: 1, count: 32), server = Data(repeating: 2, count: 32)
let key = Wire.derive(secret: secret, client: client, server: server, direction: "c2s")
var cipher = FrameCipher(key: key)
let plain = Data("Nearport compatibility fixture".utf8)
let fixture: [String: String] = ["secret": secret.base64EncodedString(), "client": client.base64EncodedString(), "server": server.base64EncodedString(), "key": key.base64EncodedString(), "plain": plain.base64EncodedString(), "frame": try cipher.seal(plain, nonce: Data(repeating: 3, count: 12)).base64EncodedString(), "helloProof": Wire.hmac(secret, "nearport/1/hello|fixture|nonce|Android")]
print(String(data: try JSONSerialization.data(withJSONObject: fixture, options: [.prettyPrinted, .sortedKeys]), encoding: .utf8)!)
