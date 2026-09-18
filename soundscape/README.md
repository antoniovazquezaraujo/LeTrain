# soundscape

Ambient audio for LeTrain as a **standalone module**: it does not depend on `core` and knows nothing
about trains, tracks or timetables. It receives the ambient state (exact time, zone weights, height,
weather) and returns and plays the target mix of every sound.

Design and decisions: `docs/developer/adr/ADR-023-Soundscape-Isolation.md`.

## Status

Working module: style format, composition engine, a catalog of 45 normalized ambient assets and
**live playback** (each material loops as a whole; random-jump crossfades are parked until the
materials are cut to loop cleanly). Rain, thunder and waves have several variants per pattern
(`weather/rain-0*.wav`, `weather/thunder-0*.wav`, `sea/waves-0*.wav`); one is picked at random
each time a style is loaded. The `synth/` folder holds procedurally generated variants of wind,
rain and waves, audited with `styles/synth-lab.sound`. CLI test player and Swing playground included. Mines and
factories have no audio yet.

## Usage

```bash
mvn -pl soundscape test

# Mix at a given moment
mvn -pl soundscape exec:java@cli \
  -Dexec.args="--time 23:30 --zones sea=0.5,fields=0.7 --height 0.2 --weather drizzle"

# Full-day timeline
mvn -pl soundscape exec:java@cli \
  -Dexec.args="--day --zones gold-mine=0.7,fields=0.4 --weather clear"
```

Options: `--file`, `--time HH:mm`, `--zones zone=weight,...`, `--height 0..1`, `--weather preset`,
`--rain/--wind/--storm 0..1`, `--day`, `--check-assets`, `--help`.

`--check-assets` resolves the style materials under `sounds/` and reports the missing ones.

## Testing GUI

`SoundscapePlayer` is a Swing workshop: sliders for time, height, weather and zones, speed presets
(slow/normal/fast, 60/40/20-minute days) with a preview multiplier (x1/x10/x60), live volume bars
per sound, a full-day button, a **🔊 Listen** button (plays the live mix; master gain with a soft
limiter) and style loading (`*.sound`).

```bash
mvn -pl soundscape exec:java@gui

# with a specific style
mvn -pl soundscape exec:java@gui -Dexec.args="my-style.sound"
```

## Style format

Plain text with sections and `key = value` lines; comments start with `#`. The full example lives in
`src/main/resources/styles/valle-norte.sound`:

| Section | Content |
|---|---|
| `[climate]` | weather presets: `rain`, `wind`, `storm` (0.0–1.0) |
| `[sounds]` | catalog: `name = material(s)` (wildcards allowed) |
| `[zones]` | `zone = sound, sound…` |
| `[presence]` | 7 values per sound: dawn, morning, noon, afternoon, dusk, night, predawn |
| `[height-by-sound]` | sensitivity to height (hawks +, cicadas −) |
| `[climate-by-sound]` | sensitivity to rain, wind and storm |
| `[weather]` | sounds contributed by the weather itself (volume = intensity) |
| `[height]` | sounds contributed by the listening height (volume = height; the altitude wind) |
| `[gains]` | optional per-sound loudness multiplier (1.0 = unchanged); keys are catalog sounds or `weather-*` / `height-*` |

Every sound is composed as `zone weight x presence (exact time) x climate x height x gain`; weather
sounds are summed separately at their intensity. Everything is deterministic: same inputs, same mix.

## Calibration

The right panel of the GUI lists every sound (weather and height sounds included) with its 0–200%
gain slider, its percentage and a live VU meter, so each fader can be set while watching the level.
*Export style…* writes the current calibration to a new `*.sound` file: the original text is
preserved and only the `[gains]` section is replaced, so comments and layout stay intact. Gains
left at 100% are omitted from the exported file.
