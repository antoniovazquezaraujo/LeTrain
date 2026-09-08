# Action Catalogue — Editing actions vs. command set (PoC, issue #487)

> Status: PoC deliverable 1 (action catalogue) — **complete** (issue #487 closed). Companion of
> ADR-020 (command journal / scenarios).
> Scope: **constructive & initial-configuration** editing actions only. Runtime operations are
> deliberately out of scope (see [Out of scope](#out-of-scope)).

## 1. Purpose

Before building the scenario language / journal machinery (ADR-020 roadmap), issue #487 asks to
prove that **every user editing action is expressible as a command** and that replaying those
commands on a fresh same-seed world reproduces the same result.

This document is the **action catalogue (matrix)**: an inventory of every world-mutating editing
action, its UI entry point, whether a DSL command exists today, and the engine method behind it,
with side-effect / determinism notes. Gaps found here drive the "minimal missing commands" chunk
(deliverable 2) and the golden replay test coverage (deliverable 3).

Legend:

- ✅ **Command exists** — expressible today via the console DSL.
- ⚠️ **Partial / indirect** — expressible, but only through a workaround or a non-obvious path.
- ❌ **Gap** — no faithful command today; a minimal command is proposed for a future iteration.

Reference abbreviations used below:

- DSL entry point: `PlayerCommandExecutor` (`core/.../command/PlayerCommandExecutor.java`).
- Script/automation language: `CommandManager` + `ScriptLogicParser` (`core/.../command/`).
- Engine builders: `RailTrackMaker` (turtle + UI construction), `Model` (state + element APIs).
- Grammar tokens: `PlayerCommandsParser.g4`, `ScriptLogicParser.g4`, `LeTrainLexer.g4`.

## 2. Action catalogue (matrix)

### 2.1 Cursor navigation & map (non-mutating, journaled)

These actions do not mutate the world but are part of the editing journal (they position the cursor).

| User action | UI (mode) | DSL command | Engine | Notes |
| :--- | :--- | :--- | :--- | :--- |
| Move cursor by coordinates | — | ✅ `go <x>,<y>;` | `Model.cursor` | Absolute positioning. |
| Move cursor to entity | — | ✅ `go <entity> <id>\|"name";` | `Model.getX()` | Cursor jumps to entity's track. |
| Move cursor along rail (topological) | arrows | ✅ `go next/prev <entity>;` · `gn/gp` | `goCommand` visitor | Follows connectivity. |
| Move to end of line | — | ✅ `go end;` | `goCommand` visitor | |
| Face cursor to a compass dir / entity | — | ✅ `face <dir>;` / `face <entity> <id>;` | `Model.cursor.setDir` | Sets heading (45° rule applies on next build). |
| Save / restore cursor position | — | ✅ `mark <name>;` · `go mark <name>;` | `Model.marks` | |
| Advance cursor without building | arrows | ✅ `move <n>[, ...];` (turtle) | `TurtleBuilder.moveForward` | Moves 1+ cells; over rails it follows curves. |

### 2.2 Rail construction (network layer)

| User action | UI (mode) | DSL command | Engine | Side effects / determinism notes |
| :--- | :--- | :--- | :--- | :--- |
| Build straight / curved track, 1+ cells | `Shift`+arrow (RAILS) | ✅ `write <n>[, r/l, ...];` | `RailTrackMaker.makeTrack` / `createTrack(null)` | One piece per built cell; cursor advances. Connects to previous piece (`oldTrack`). Fires `onRailTrackConstructed` (economy). |
| Turn during build | arrows (RAILS) | ✅ `write …, r / l, …;` | `RailTrackMaker.cursorTurnLeft/Right` | After a turn without a distance, 1 cell is implied. **45° rule**: `Math.abs(oldDir.inverse().angularDistance(dir)) > 1` aborts placement (`makeTrack`). |
| Bridge over water | build over WATER | ✅ `write` (terrain-derived) | `RailTrackMaker.detectTrackType` → `BRIDGE_TRACK` | Type chosen by terrain under cursor, not by player. Construction has a delay (economy). |
| Tunnel in rock | build over ROCK | ✅ `write` (terrain-derived) | `detectTrackType` → `TUNNEL_TRACK` | Same as bridge; delayed construction. |
| Water/rock gate pieces | leaving a tunnel/bridge onto GROUND | ✅ `write` (automatic) | `RailTrackMaker.convertOldTrackToGate` | Previous tile is replaced by a `BRIDGE_GATE`/`TUNNEL_GATE` copy preserving routes. Jumping water↔rock without GROUND is illegal and does not build. |
| Create a fork (switch) | drawing a 3rd exit from a tile | ✅ `write` (auto-promotion) | `RailTrackMaker.createForkRailTrack`, `canBeAFork` | A tile is promoted to `ForkRailTrack` (new `nextForkId`) when it gains a 3rd route. Not a direct player command in UI either. |
| Delete a rail cell (forward) | `Ctrl`+`↑` (RAILS) | ✅ `del <n>;` (turtle) | `RailTrackMaker.removeTrack`, `Model.removeTrack` | **ADR-005**: occupied track (train present) is never removed. Turtle `del` clears the train on the cell first, then removes the track. Cascade: the piece is unlinked from neighbours; a `ForkRailTrack` is deregistered; **a mounted component is removed too** (element + rail together). |
| Delete a rail cell (backward) | `Ctrl`/`Shift`+`↓` (RAILS) | ✅ `del` after `move` / reposition | `removeTrack` | The cursor backs up first, then removes the cell it was on. |

### 2.3 Fixed elements on a rail cell (network layer)

A rail cell hosts **one component**: `station`, `sensor`, `semaphore` or `speed signal`.
Placement requires a free cell (`component == null`); the element orientation is derived from the
cursor heading at creation time.

| User action | UI (mode) | DSL command | Engine | Side effects / determinism notes |
| :--- | :--- | :--- | :--- | :--- |
| Place a station | `End` (RAILS) / `n` (ADD) | ✅ `new st;` / `new station;` | `Model.addStation`, `applyStationRoleByIndustry` | Cursor must be aligned with the rail. `creationDir` = cursor dir; `sideDir` = opposite. **Role is re-derived from nearest industry (radius 5)** at creation → cargo/role/storage depend on the world. Producer stations start with storage 50. |
| Remove a station | `End` again (toggle) | ✅ `del st [id];` / `del station [id];` | `Model.removeStation` | Only when the cell component is a `Station`. Component slot cleared; economy `onStationDestroyed`. |
| Place a sensor | `Insert` (RAILS) / `e` (ADD) | ✅ `new sn;` / `new sensor;` | `Model.addSensor` | `creationDir` = cursor dir. Sensor fires enter/exit events (system listener added at creation). |
| Remove a sensor | `Insert` again (toggle) | ✅ `del sn [id];` / `del sensor [id];` | `Model.removeSensor` | Plain sensor only (excludes station/signal/semaphore). |
| Place a semaphore | `Home` (RAILS) / `s` (ADD) | ✅ `new sm;` / `new semaphore;` | `Model.addSemaphore` | Registered as cell component. State (open/closed) is runtime, not editing. |
| Remove a semaphore | `Home` again (toggle) | ✅ `del sm [id];` / `del semaphore [id];` | `Model.removeSemaphore` | |
| Place a speed signal | `Delete` (RAILS) / `g` (ADD) | ✅ `new sg;` / `new signal;` | `Model.addSensor` (SpeedSignal) | Created with default limit **3**, mode **max**. |
| Remove a speed signal | `Delete` again (toggle) | ✅ `del sg [id];` / `del signal [id];` | `Model.removeSensor` | |
| **Move an element forward along the rail** (1 resting cell / hop) | `Shift`+`↑` / `K` (STATIONS/SENSORS/SEMAPHORES/SPEED_SIGNALS) | ✅ `slide <st\|sn\|sm\|sg> <id> fw [n];` | `Model.moveSensorForward` | Jumps over other components, **never over trains**, crosses forks following the active branch, stops at end of line. A moved `Station` **re-derives its industry role** at the destination (mirror of creation). Element orientation follows the rail (`continuationDir`). |
| **Move an element backward along the rail** | `Shift`+`↓` / `J` (same modes) | ✅ `slide <el> <id> bw [n];` | `Model.moveSensorBackward` | Same rules; uses the rail end opposite the facing. |
| **Invert orientation of a station** (flip facing + platform side) | `Space` (STATIONS) | ✅ `st <id> invert;` / `station <id> invert;` | `CommandManager.visitDirectStationCommand` → `Station.flipOrientation` | Inverts `creationDir` and recomputes `sideDir` (platform switches side). |
| **Invert orientation of a sensor** | `Space` (SENSORS) | ✅ `sn <id> invert;` / `sensor <id> invert;` | `CommandManager.visitDirectSensorCommand` → `setCreationDir(inverse)` | Flips detection direction in place. Only applies to plain sensors (never speed signals / stations sharing a numeric id). |
| Invert orientation of a semaphore | `Space` (SEMAPHORES) | ✅ `sm <id> invert;` / `semaphore <id> invert;` | `CommandManager.visitDirectSemaphoreCommand` | Flips `creationDir`. Note: `invert` here is **orientation**, not open/closed state. |
| Invert orientation of a speed signal | `Space` (SPEED_SIGNALS) | ✅ `sg <id> invert;` / `signal <id> invert;` | `CommandManager.visitDirectSignalCommand` | Flips `creationDir`. |
| Configure speed-signal limit (1-10) | digits / `↑` `↓` (SPEED_SIGNALS) | ✅ `sg <id> set limit <n>;` | `Signal.setLimit` | Persistent property of the element. |
| Toggle speed-signal max/min ("end of limit") | `m` (SPEED_SIGNALS) | ✅ `sg <id> set mode max\|min;` | `Signal.setMax` | |
| Rename a station / sensor | (STATIONS/SENSORS) | ✅ `<st\|sn> <id> set name "…";` | `CommandManager` / `visitSetNameCommand` | Naming is deterministic config. |

### 2.4 Switch (fork) configuration

| User action | UI (mode) | DSL command | Engine | Notes |
| :--- | :--- | :--- | :--- | :--- |
| Select a fork (prev/next / by id) | `←`/`→`, digits (FORKS) | ✅ `go fk <id>;` / `go next fork;` | `Model.select*Fork` | Positioning only. |
| Set fork route straight / curved | `Space`, `↑`/`↓` (FORKS) | ✅ `fork <id> set straight;` / `set curved;` | `CommandManager.visitDirectForkCommand` | Direct command honours a compass direction too (`fork <id> set e;`). |
| Flip fork route | `Space`, `↑`/`↓` (FORKS) | ✅ `fork <id> flip;` | `Model` / fork flip | **Borderline runtime**: flipping an active switch also wakes/resets train safety timers; decide per ADR-020 layer whether it belongs in `on build` or `on start`. |

### 2.5 Vehicles / fleet (operator layer)

| User action | UI (mode) | DSL command | Engine | Notes |
| :--- | :--- | :--- | :--- | :--- |
| Choose wagon cargo type | `1/2/3` (TRAINS) | ✅ `new wagon <aspect> coal\|gold\|ruby;` | `Model.setSelectedWagonType` | Cargo passed as an argument in DSL. |
| Choose locomotive colour | digit after spawn (TRAINS) | ✅ `new loco <aspect> <color>;` | `Locomotive.setColor` | Colour argument in DSL. |
| **Spawn a locomotive** (new train, operator origin) | uppercase letter (TRAINS) | ✅ `new loco A red;` | `Model.addLocomotive`, `Train` + `claimOccupiedSegments` | The aspect letter is the locomotive "class". A new `Train` (id `nextTrainId`) is created and the loco becomes director. Blocks/segments are claimed. |
| **Spawn a loose wagon** | lowercase letter (TRAINS) | ✅ `new wagon b ruby;` | `Model.addWagon` | No train is created (loose wagon). |
| Couple vehicles to a train | (LINK mode) | ✅ `train <id> couple fw\|bw [n];` | `TrainCouplingManager.joinLinkers` | n defaults to all (`0`) in UI, to a count in DSL. |
| Uncouple / split a train | (UNLINK mode) | ✅ `train <id> uncouple fw\|bw [n];` | `TrainCouplingManager.divideTrain` | Splitting creates a new `Train` (`nextTrainId`). |
| Delete a whole train | `Backspace` on a vehicle / `clear` (TRAINS) | ✅ `clear tr <id>;` / `clear train <id>;` | `Model.removeLocomotive/removeWagon` (per linker) | Frees every occupied track. |
| Delete the vehicle under the cursor (whole train if it is one, or a single loose wagon/loco) | `Backspace` (TRAINS) | ✅ turtle `clear;` at the cell (`go <x>,<y>; clear;`) — or `clear train <id>` for a whole train by id | `clearTrainAtCursor` (`PlayerCommandExecutor`) + `Model.removeLocomotive` / `removeWagon` | `clear` at a cell is **positional and all-or-nothing**: if the linker on that cell belongs to a train it removes the whole train; if it is a loose wagon/loco (or empty train) it removes just that vehicle. This already covers the loose-vehicle case without a `del wagon`/`del loco` command. |
| Rename a train | — | ✅ `train <id> set name "…";` | `visitSetNameCommand` | |
| Assign an itinerary + autopilot | (PROGRAM) | ✅ `create itinerary "…" {…};` + `assign itinerary "…" to train <id>;` + `train <id> set autopilot true;` | `CommandManager` | `on start`/operator origin; waypoints on stations/sensors with load/unload/reverse/stop/wait/speed. |

## 3. Gaps found (deliverable 2 input)

The catalogue originally flagged two gaps, both now implemented:

| # | Editing action | Command implemented | Where |
| :--- | :--- | :--- | :--- |
| 1 | Invert station orientation | `station <id> invert;` → `Station.flipOrientation()` | PR #491 (`CommandManager.visitDirectStationCommand`) |
| 2 | Invert sensor orientation | `sensor <id> invert;` → `creationDir = inverse()` | PR #491 (`CommandManager.visitDirectSensorCommand`) |

> Vehicle deletion is **not** a gap: a loose wagon/loco is removed positionally with turtle `clear;`
> at its cell, and a whole train with `clear train <id>;`. Only a "delete a single linker out of the
> middle of a multi-vehicle train" would be unexpressible, and that is better done as
> `uncouple` + `clear` (fleet composition, out of the editing scope of this PoC).

> **Conclusion of the PoC:** every world-mutating editing action (constructive and initial-config
> layers of ADR-020) maps to an existing command, to `slide`, or to the turtle/auto-promotion
> machinery. No inexpressible or non-deterministic editing action was found.

## 4. Golden test coverage

Mapping between the PoC golden replay test (`core/src/test/java/letrain/PocJournalReplayTest.java`)
and the catalogue rows above:

| Catalogue area | Exercised by the golden script |
| :--- | :--- |
| Straight construction + cursor advance (`write 8`) | ✅ plain line |
| Curve construction (`write 4, r, 3`) | ✅ SE diagonal segment |
| Fork auto-creation (3rd exit drawn) | ✅ diverging branch (asserts 1 fork) |
| Bridge + gates over water (`write` over GROUND→WATER→GROUND) | ✅ (asserts ≥3 bridge/gate tiles) |
| Tunnel + gates in rock + delete of a tile | ✅ rock crossing + `del 1` |
| Place / remove station, sensor, semaphore | ✅ `new st/sn/sm` (one each) |
| Move element along the rail | ✅ `slide sn 1 fw 2` + byte-identical assertion |
| Spawn locomotives (operator origin) | ✅ `new loco A red`, `new loco B blue` (asserts 2) |
| Station/sensor orientation invert | ✅ command + unit tests (`StationSensorInvertTest`); not yet exercised inside the golden script |
| Speed signal placement / invert | ⏳ not exercised in golden script (unit-level coverage only) |

The golden assertion is: replaying the *same* script on two fresh copies of the *same* serialized
base world produces **byte-identical** model states (plus structural assertions per row).

## 5. Out of scope

Not part of the editing catalogue (runtime / simulation state, by ADR-020 design):

- Driving a train (accelerate/decelerate/set speed), engine on/off, live reverse.
- Opening/closing semaphores during play; flipping forks at runtime.
- Load/unload and industrial actions at stations.
- Money / economy drift and construction delays at runtime.
- Track-element **event logic** (triggers) and itinerary execution — these are registered
  program text (`on start`-style), not one-shot editing commands.

## 6. References

- Issue: [#487 — PoC: every user editing action expressible as a command with faithful replay](https://github.com/antoniovazquezaraujo/LeTrain/issues/487)
- PR #489 (PoC: slide + golden + this catalogue) and PR #491 (station/sensor invert, merged to `develop`)
- ADR-020: Command journal / scenarios (proposed, `docs/developer/adr/ADR-020-Command-Journal-Scenarios.md` on branch `docs/adr-020-command-journal`)
- DSL reference: `core/src/main/antlr4/letrain/command/*.g4`, `docs/user/grammar.md`
- Element movement contract tests: `core/src/test/java/letrain/track/TrackElementMoveTest.java`, `core/src/test/java/letrain/command/SlideCommandTest.java`
- Orientation invert contract tests: `core/src/test/java/letrain/command/StationSensorInvertTest.java`
