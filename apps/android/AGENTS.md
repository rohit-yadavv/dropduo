# Android contributor instructions

Read root AGENTS.md. Kotlin/Compose UI in app; transport and cryptography in core (pure JVM). No Android APIs in core. Use content URIs and platform pickers; do not request all-files access. Network and file IO off main thread. Connection service has an explicit foreground notification and user-controlled stop. Keystore protects secrets. Check with the Gradle wrapper. Never claim emulator results prove physical-device background reliability.
