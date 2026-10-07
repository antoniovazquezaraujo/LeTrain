# Issue #721 — day cursor captures

Before/after of the 2D cursor colours on the light green field, generated with the real
`TerminalPalette` (`resolve(ratio)`: truecolor RGB + ANSI-16 fallback) at ratio 0 (day), 0.5
(dusk) and 1 (night). The `*.txt` files are the numeric dumps (luminance `L` and contrast `Δ`
against the field), `*.html` the preview and `*.png` its rendered snapshot.

| State (day) | Before (`develop`) | After (final) |
|---|---|---|
| `CURSOR_DRAWING` | `#006D28` (L 80.8, Δ 55.4) | **`#000000`** (L 0, Δ 136.3) |
| `CURSOR_MOVING` | `#665300` (L 81.0, Δ 55.2) | **`#000000`** (L 0, Δ 136.3) |
| `CURSOR_ERASING` | `#BE2828` (L 71.9, Δ 64.4) | **`#000000`** (L 0, Δ 136.3) |

After the owner's playtest, the day keys are pure black: maximum contrast against the field
(`MIN_CONTRAST = 55`), ANSI-16 fallback `BLACK` (never the field's `GREEN`), and the day→night
fade keeps `Δ ≥ 55` at every 0.05 step with the palette's usual polarity inversion. Dusk and
night are unchanged.
