# Security design

Pairing uses a random 256-bit secret in a short-lived QR ticket and explicit Mac approval. Anyone viewing an unused ticket may attempt pairing; tickets expire and are consumed once. Never send tickets to a remote analytics service.

Sessions use fresh random nonces, HMAC transcript authentication, HKDF-SHA256 directional keys, and AES-256-GCM with monotonic sequence numbers authenticated as additional data. Reject replays, invalid tags, wrong versions, unknown peers, and oversized frames. This is a small documented application protocol using standard primitives, not an independently audited protocol. No forward secrecy in v1: disclosure of a pair secret can expose captured sessions. Re-pair after compromise.

Validate file IDs, names, sizes, offsets, and hashes. Stream bounded chunks to partial files; do not overwrite user files. Verify SHA-256 before finalizing. Only the requested transfer is accessible; never expose arbitrary paths. Bound active transfers and free space, clean abandoned partials, and allow users to cancel.

Store secrets in native secure storage. Metadata/history may reveal filenames and peer names locally. No telemetry. Authentication doesn't make received files safe to execute; never auto-open them.
