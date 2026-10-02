# ADR 0001: native apps and versioned contract

Accepted for v1. Use SwiftUI/macOS and Kotlin/Compose/Android in one repo. Prefer system integration and two independently tested protocol implementations over a shared cross-platform UI or premature shared engine. Mac hosts the local connection; Android connects, and both send. Keep APIs versioned for future platforms.
