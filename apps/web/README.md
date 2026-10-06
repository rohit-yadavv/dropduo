# Website

Dependency-free HTML, CSS, and JavaScript. Build with `./scripts/check web`, then preview using `python3 -m http.server 4173 -d dist/web`.

- `index.html`, `styles.css`, `main.js`: page; system light/dark theme with remembered toggle (`dropduo:theme`), reduced-motion support.
- GitHub Releases API provides downloads. Keep asset patterns aligned with [asset names](../../docs/releases.md#asset-names). Without a release, link development builds; API failures fall back to Releases.
- Desktop Android downloads include a QR code; vendor script loads only when needed.
- `mac-install.html`: separate first-launch page and source/contribution/checksum links. The best-effort `x-apple.systempreferences:com.apple.preference.security` link opens Settings, never approves the app. Keep manual fallback; native navigation needs Mac verification.
- `assets/video/`: the silent 30-second demo (H.264, faststart) and its poster. It autoplays muted in view, never with reduced motion. Re-encode for size before replacing it.
- Inline mark mirrors `branding/mark.json`; build copies icon exports to `assets/brand/`. Android screenshots are in `assets/img/`; a Mac screenshot is still wanted.

Vendored assets: qrcode-generator 2.0.4 (MIT), Geist fonts (OFL 1.1), Phosphor Icons 2.1.1 (MIT), and Simple Icons 16.33.0 Apple/Android logos (CC0; trademarks retained). Keep license files and [dependency notices](../../docs/dependencies.md) current.
