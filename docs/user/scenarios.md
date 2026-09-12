# LeTrain - Scenarios (`.ltr`)

A **scenario** is a plain-text recipe that rebuilds a world from a terrain **seed**: the
infrastructure, the initial conditions and the operator program. It is *not* a savegame.

| | Savegame | Scenario (`.ltr`) |
|---|---|---|
| What it is | The current **state** (positions, money, trains in motion) | The **recipe** that builds a world |
| Continue where you left off | Yes | No |
| Reproducible on a fresh world | No | Yes (same seed → same world) |
| Free construction (no costs) | No | Yes |

Both artifacts are self-contained and are never mixed.

## File format

```
# LeTrain scenario v1
seed 123
configuration {
  threshold.WATER=130
}
on build {
  go 0,0; face e; write 5;
  new st;
}
on start {
  semaphore 1 close;
}
program {
  sensor 1 on train enter { semaphore 1 open; }
}
```

- `seed <n>` — **required**. The terrain seed; the same seed reproduces the same terrain.
- `configuration { ... }` — game settings as `key=value` (the same keys as `letrain.cfg`). The
  scenario carries its own rules and they **win over** the local `letrain.cfg`.
- `on build { ... }` — the construction commands (tracks, elements, trains). Replayed once, first.
- `on start { ... }` — optional initial conditions applied right after the build (e.g. semaphore
  states).
- `program { ... }` — the automation script (itineraries, triggers). Installed after the build, so
  it can reference the freshly built elements.

All sections are optional and can appear in any order; `#` starts a comment. A flat file with just a
seed and commands (no braces) is valid and treated as `on build`, so older scenarios keep working.

## Creating and playing

1. Turn on **paused editing** (`x`) so the world is frozen and the journal is exact.
2. Build and edit (keyboard or console). Recording is toggled with **`R`**; the journal is what an
   export replays.
3. Open the editor (`p`) and **Export** (or use the console `export`). You get a `.ltr` file.
4. **Import** a `.ltr` (editor) or play it: the world is rebuilt on a fresh model with that seed, in
   **free construction mode** (no costs) and with paused editing on.

While paused, edits can be undone/redone with `u` / **Ctrl+R** (or `undo;` / `redo;`).

## Validating a scenario

`letrain-check` validates a `.ltr` **without launching the game** (syntax only):

```bash
letrain-check my-scenario.ltr
```

- exit `0` if valid, `1` if there are diagnostics, `2` for usage/IO errors.
- each diagnostic is printed as `path:line:col: error: message`, so editors such as vim or VS Code
  can jump straight to the line.

## The editor

Press `p` to open the **LeTrain Editor**. Its three tabs together make up the scenario:

- **Scenario** — `seed` + `on build` + `on start`.
- **Program** — the `program { ... }` block.
- **Config** — the `configuration { ... }` settings.

The quick reference on the right shows only the commands that make sense for the active tab;
selecting one inserts it on a new line. The footer holds `Save`/`Load` (savegame),
`Export`/`Import` (scenario file), `Refresh` (reload from the world), `Reprogram` (apply the
Program) and `Rebuild` (validate and play the scenario). `Esc` closes the editor. When there are
errors, a small scrollable list lets you jump to the offending line.

---

See also: **[grammar.md](grammar.md)** for the command language and the console.
