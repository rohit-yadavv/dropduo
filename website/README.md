# DropDuo website

Static landing and download page. No framework, bundler, or package install.

- `index.html`, `styles.css`, `main.js`: the page. Light and dark follow the visitor's system setting; motion is disabled under `prefers-reduced-motion`.
- The inline mark in `index.html` mirrors `branding/mark.json`. Update both if the mark changes. `scripts/build-website` copies the generated icon files into `assets/brand/`.
- Downloads come from the GitHub Releases API at view time. Asset names must keep the pattern in [downloads](../docs/downloads.md) (`-macos-arm64`, `-macos-x86_64`, `-android` with optional `-development`). With no published release, the page shows a pending state linking to development builds.
- `assets/img/` holds real Android emulator screenshots (light and dark). Recapture them when the home screen changes. A Mac app screenshot is still wanted.

Third-party assets: Geist and Geist Mono fonts (SIL OFL 1.1, `assets/fonts/LICENSE-geist.txt`) and Phosphor Icons 2.1.1 regular (MIT, `assets/LICENSE-phosphor.txt`), compiled into `assets/icons.svg`.

Build and preview: `./scripts/check website`, then `python3 -m http.server 4173 -d dist/website`.
