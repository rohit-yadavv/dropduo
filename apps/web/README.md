# DropDuo website

Static landing and download page. No framework, bundler, or package install.

- `index.html`, `styles.css`, `main.js`: the page. Light and dark follow the system setting until the visitor uses the nav toggle, which is remembered in `localStorage` (`dropduo:theme`). Theme colors key off `:root[data-theme]`, set before first paint by the head script; motion is disabled under `prefers-reduced-motion`.
- The inline mark in `index.html` mirrors `branding/mark.json`. Update both if the mark changes. `scripts/build-web` copies the generated icon files into `assets/brand/`.
- Downloads come from the GitHub Releases API at view time. Asset names must keep the pattern in [downloads](../../docs/downloads.md) (`-macos-arm64`, `-macos-x86_64`, `-android` with optional `-development`). With no published release, the page shows a pending state linking to development builds.
- `assets/img/` holds real Android emulator screenshots (light and dark). Recapture them when the home screen changes. A Mac app screenshot is still wanted.

On computers, the Android download is also shown as a QR code (level H, app tile in the centre) that points straight at the APK; `assets/vendor/qrcode.js` loads only then.

Third-party assets: qrcode-generator 2.0.4 (MIT, `assets/vendor/LICENSE-qrcode-generator.txt`), Geist and Geist Mono fonts (SIL OFL 1.1, `assets/fonts/LICENSE-geist.txt`) and Phosphor Icons 2.1.1 regular (MIT, `assets/LICENSE-phosphor.txt`), plus the Apple and Android logos from Simple Icons 16.33.0 (CC0 1.0; the logos remain their owners' trademarks), compiled into `assets/icons.svg`.

Build and preview: `./scripts/check web`, then `python3 -m http.server 4173 -d dist/web`.
