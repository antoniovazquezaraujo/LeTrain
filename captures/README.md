# Issue #721 — day cursor captures

Before/after of the 2D cursor colours on the light green field, generated with the real
`TerminalPalette` (`resolve(ratio)`: truecolor RGB + ANSI-16 fallback) at ratio 0 (day), 0.5
(dusk) and 1 (night). The `*.txt` files are the numeric dumps (luminance `L` and contrast `Δ`
against the field), `*.html` the preview and `*.png` its rendered snapshot.

| State (day) | Before | After |
|---|---|---|
| drawing | `#006D28` (L 80.8, Δ 55.4) | `#005A1E` (L 66.5, Δ 69.7) |
| moving | `#665300` (L 81.0, Δ 55.2) | `#5F4600` (L 70.3, Δ 66.0) |
| erasing | `#BE2828` (L 71.9, Δ 64.4) | `#AA1E1E` (L 59.8, Δ 76.5) |

Dusk and night are unchanged.
