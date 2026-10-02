# DropDuo identity

The approved mark is the bold, overlapping double-D monogram. The upper-left D is ink (`#202124`), the lower-right D is cobalt (`#2558DD`), and the app-icon background is pure white (`#FFFFFF`). Use solid fills with clean intersections. No gradients, shadows, extra file glyphs, or warm-white backgrounds.

`mark.json` is the editable geometry and palette source. It defines two even-odd paths and a clipped repeat of the first letter to preserve the woven overlap. `dropduo-mark.svg` has a transparent background, `dropduo-icon.svg` adds a white tile, and `dropduo-monochrome.svg` is for single-color uses. `dropduo-icon.png` is the desktop preview/export.

Run `./scripts/generate-branding` after editing the source. It uses Python 3's standard library for SVG and Android vector exports. On macOS it also runs the Swift/CoreGraphics renderer and Apple's iconutil to regenerate the committed Mac PNGs and ICNS. It does not call image-generation services or install dependencies. Review changes at small sizes before committing. Do not edit generated assets individually.

The Mac bundle uses `Resources/DropDuo.icns`; SwiftPM resources provide the in-app mark and a template menu icon. Android uses a white-background adaptive launcher icon, a separate monochrome layer for themed launchers, and a transparent white notification glyph. Launcher masking is owned by Android; no square or rounded tile is baked into the foreground. The full-color in-app vector sits on a white surface even in dark mode.

AI-generated concepts were used during exploration. Production assets are an authored vector redraw of the selected concept, with deterministic platform exports. Exploratory images and prompts remain in ignored `.artifacts/icon-concepts`; apps do not depend on them.

Platform references: [Android adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive), [Android app and notification assets](https://developer.android.com/studio/write/create-app-icons).
