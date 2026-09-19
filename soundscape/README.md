# soundscape

Ambient audio for LeTrain as a **standalone module**: it does not depend on `core` and knows nothing
about trains, tracks or timetables. It receives the ambient state (exact time, zone weights, height,
weather) and returns and plays the target mix of every sound.

Design and decisions: `docs/developer/adr/ADR-023-Soundscape-Isolation.md`.

## Status

Working module: style format, composition engine, a catalog of 36 normalized ambient assets and
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
| `[distance-by-sound]` | optional distance sensitivity (0 = at the listener, 1 = full); same keys as `[gains]` |
| `[min-distance]` | optional per-sound minimum distance (0 = can be next to the listener); fauna such as cicadas uses it to always sound away |
| `[silence-when]` | behaviour gates: `crickets = rain > 0.25`; one condition per line, repeatable |

Every sound is composed as `zone weight x presence (exact time) x climate x height x gain`; weather
sounds are summed separately at their intensity. Everything is deterministic: same inputs, same mix.

Sounds produced away from the listener (zone and weather sounds) carry a **distance** from the
listening height: the engine attenuates them up to -8 dB and the player turns distance into air
absorption (a low-pass from 20 kHz on the ground down to 1.2 kHz when zoomed out). The ground world — rain hitting the ground,
wind in the trees — recedes, while the height-provided sounds (altitude wind) stay next to the
listener. `[distance-by-sound]` overrides the sensitivity per sound (0 keeps it next to the listener,
1 recedes fully), e.g. `hawks = 0.3`.

Behaviour gates are not fades: `[silence-when]` stops a sound when rain, wind or storm go above a
threshold, easing the change over a tiny band (0.05) so there is no click. Crickets and cicadas do
not sing in the rain, they simply stop.

## Calibration

The right panel has two tabs:

- **Mix**: every sound (weather and height sounds included) with its 0–200% gain slider, its
  percentage and a live VU meter, so each fader is set while watching the level.
- **Responses**: distance sensitivity and the rain/wind/storm silence gates of every sound
  (100% = gate off).

*Export style…* writes the current calibration to a new `*.sound` file: the original text is
preserved and only the `[gains]`, `[distance-by-sound]` and `[silence-when]` sections are replaced, so
comments and layout stay intact. Values equal to the defaults are omitted.
