# Nearport protocol v1

## Transport and discovery

Mac advertises `_nearport._tcp` (default port 53318). Android opens TCP; either peer may initiate a transfer. Discovery names/addresses are untrusted. Big-endian unsigned 32-bit length prefixes each frame; reject lengths outside 1..400000 before allocation. Handshake frames have a 4096-byte limit. UTF-8 JSON control messages; unknown protocol versions are rejected.

## Pairing

`nearport://pair/<standard-base64-JSON>` encodes version, pairID (UUID), host, port, secret (base64 32 random bytes), name, expires (Unix seconds). Ticket expires after five minutes. QR material is sensitive. Mac verifies proof before asking for approval; consume once and save the secret only on approval. Paired devices reuse their secret, not the expiry or old IP as identity. Secrets never appear in ordinary metadata or logs.

## Session authentication

Android hello: `{version:1,pairID,name,nonce,proof}`. Client nonce is 32 fresh random bytes in base64. Proof is base64 HMAC-SHA256(secret, UTF-8 `nearport/1/hello|pairID|nonce|name`).

Mac welcome: `{version:1,nonce,proof}`. Server nonce is 32 fresh random bytes. Proof authenticates `nearport/1/welcome|pairID|clientNonceBase64|serverNonceBase64`.

HKDF-SHA256 uses secret as IKM, concatenated raw client/server nonces as salt, and UTF-8 `nearport/1/c2s` / `nearport/1/s2c` as info. Output is separate 32-byte directional keys.

## Encryption

Each encrypted frame contains an 8-byte big-endian sequence, 12-byte random GCM nonce, ciphertext, and 16-byte authentication tag. AES-256-GCM authenticates the sequence bytes as AAD. Counters start at zero independently in each direction; accept only the exact next sequence. Authentication failure closes the session. Standard primitives; no forward secrecy; independent security audit outstanding.

## Messages

All messages have type and a UUID id. Fields are omitted when unused.

| Type | Fields | Behavior |
|---|---|---|
| offer | id,name,size,sha256 | Validate metadata and prepare/resume a partial file |
| accept | id,offset | Receiver's verified metadata-matched partial size |
| chunk | id,offset,data | Base64 raw bytes, at most 196608 bytes; offset must equal current length |
| ack | id,offset | Confirm next offset after durable chunk write |
| finish | id | Verify complete size and SHA-256, then publish file |
| complete | id | Confirm published file or accepted text |
| cancel | id | Remove partial, stop active transfer |
| error | id,error | Transfer-scoped failure |
| text | id,text | Explicitly shared UTF-8 text, at most 64000 bytes |

Stop-and-wait chunks bound memory and simplify resume; throughput tuning follows measured results. Maximum file size is 32 GiB, up to four active incoming/outgoing files per peer. Filenames must be nonempty, at most 218 UTF-8 bytes, not dot/dot-dot, and contain no path separators or control characters. Destination prefixes a UUID to prevent collisions. Partial storage is scoped per paired peer. Invalid hashes/sizes/offsets must fail. Completed matching files make retry idempotent. Abandoned partials expire after seven days. Sender retains source and transfer ID for explicit retry; no cloud/offline delivery guarantee.

## Compatibility fixtures

`test-vectors/crypto.json` fixes secret, nonces, derived key, plaintext, encrypted frame, and HMAC proof. Swift and JVM must generate/decrypt identical bytes and reject replay and tampering.
