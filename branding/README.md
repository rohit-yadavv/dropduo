# Branding

The overlapping double-D mark uses ink `#202124`, cobalt `#2558DD`, and a pure-white `#FFFFFF` app background. Keep solid fills and clean intersections; no gradients, shadows, extra glyphs, or warm-white backgrounds.

Edit `mark.json`, then run `./scripts/generate-branding` on macOS. It uses Python's standard library plus Swift/CoreGraphics and `iconutil` for Mac assets. Review small sizes and commit exports; do not edit generated assets individually.

- `dropduo-mark.svg`: transparent mark; `dropduo-icon.svg`: white tile; `dropduo-monochrome.svg`: single color; `dropduo-icon.png`: desktop preview. The Mac app icon uses `macColors` (dark tile) so it stays legible when macOS darkens icons.
- Mac: ICNS, in-app resources, template menu icon.
- Android: white adaptive background, unmasked foreground, monochrome launcher layer, white notification glyph. Full-color in-app marks sit on white.

Production vectors are an authored redraw of an AI-explored concept. Exploratory files stay in ignored `.artifacts/icon-concepts`; builds use committed exports.
