# LeTrain - Scripting Grammar (Automation)

LeTrain includes its own lexer/parser (based on ANTLR4) that allows you to automate the railway network using a domain-specific language. Scripts are executed line by line. The same language is used from the console (direct commands) and inside the `program { ... }` section of a scenario file (see **[scenarios.md](scenarios.md)**).

## ⚠️ Warnings and errors (D1 policy)

**"Every problem warns; success stays silent."** Language problems always reach the player's visible channel (never only the log):

- A **syntax error** is shown with the failing position (column on the console; line:column inside `program { ... }`) and **executes nothing**: inside `program { ... }` the whole program is rejected (the previous one stays applied); in the console, that single order does not run.
- A **semantic rejection** (unknown entity, unknown waypoint destination, mission with no route…) also warns. An itinerary with an unknown destination is **not created**: a plan missing a stop never runs.
- A **mechanical adjustment** warns before it is applied (e.g. a speed outside `0..10` is clamped and the applied value is reported).
- When a **savegame is loaded**, its stored program is re-applied. If it no longer parses, the rejection is reported in the message panel when the load finishes (it does not stay in the log) and the text is **kept** in the editor, marked as not applied, so it can be fixed. The same panel receives every problem of the re-applied program (unknown entity, rejected itinerary…).

### Notice channel: typed command vs asynchronous event

The channel is **contextual**:

- An order **typed in the console** warns **on the console itself**: the command line shows the short notice (and stays open so you can read it). When the notice set is long or multiline (over 60 characters or containing a newline — the same threshold syntax errors use), the scrollable panel opens instead and the notice is **not duplicated** on the line.
- **Asynchronous events** (a program being loaded/applied, itinerary missions, triggers firing) always report to the **panel**, as before.

The log remains traces only: the warnings you see never depend on it.

## 🔤 Keywords and names: strict case

The language is **case-sensitive in every entry point** (console, triggers and `program { ... }`). A program runs **verbatim**: nothing is lowercased, neither keywords nor strings.

- **Keywords are strictly lowercase** (`train`, `sensor`, `create`, `assign`, `speed`, `wait`…). `TRAIN 1 set speed 3;` is a syntax error (column on the console, line:column inside `program { ... }`); a program is checked before executing, so a wrong-case keyword rejects the whole text and **nothing runs** (the previous automation stays applied).
- **Names keep their case and are matched exactly**: `station 1 set name "Central";` stores `Central`, and `station "Central"` is not the same as `station "central"`. A wrong-case reference is not found, **warns** (`not found`) and does nothing: the itinerary is not created, the trigger is not installed, the order is ignored. Always quote the name.

## ⚙️ Language Structure

The language supports three main types of statements: direct commands, itinerary creation/assignment (Autopilot), and event-triggered blocks (*triggers*).

### 1. Direct Commands
These are executed immediately. **They require a semicolon (`;`) at the end**.

**Train Actions (`trainRef` can be a number or a name in quotes):**
- `train [ID] accelerate;`
- `train [ID] decelerate;`
- `train [ID] set speed [NUM];` (the useful range is `0..10`; a value outside it is clamped to the limit and the applied value is reported)
- `train [ID] invert;` / `train [ID] reverse;` (synonyms: flip the travel sense)
- `train [ID] stop;` (brakes and turns the autopilot off)
- `train [ID] park;` (brakes, switches the engine off and **keeps** the autopilot)
- `train [ID] stop at station [ID|"name"] [speed NUM];`
- `train [ID] stop at sensor [ID|"name"] [speed NUM];`
- `train [ID] stop at end [speed NUM];`
- `train [ID] stop when blocked [speed NUM];`
- `train [ID] stop on contact [speed NUM];`
- `train [ID] set engine on;` / `train [ID] set engine off;`
- `train [ID] set forward;` / `train [ID] set backward;`
- `train [ID] load;`
- `train [ID] unload;`
- `train [ID] couple forward [NUM|all];` / `train [ID] uncouple backward [NUM|all];`
  (with no count, **both** `couple` and `uncouple` act on every vehicle on that side; a count must
  be `>= 1` or `all`, and `0` warns and does nothing)

**Reserved word `all`**: `all` is the count for `couple`/`uncouple` (`uncouple backward all`), so a bare
`all` name in any other order is a syntax error (`info station all;`); quote it to use it as a name:
`info station "all";`.

**One-shot missions (`stop at …`)**: destination and speed travel in the same order, so the train
does not start before receiving the destination. The speed is applied when the maneuver starts
(without `speed`, or with `speed 0`, the train keeps its current target; if that is 0 the order is
rejected with a warning) and the train always ends stopped. The destination can be a station, a
sensor, the end of track **ahead of the train** (the train brakes on the last rail, without touching
the buffer; a closed loop with no end ahead warns and does not move), the first block
(`stop when blocked` rolls to the last rail of its canton before the block and completes stopped
there; it does **not** resume when it is released, and if the block is freed before it stops it keeps
going) or the **vehicle ahead** (`stop on contact`): it drives at the ordered speed and completes on
the **first low-speed physical contact**, staying **pressed** against the vehicle, ready for
`couple`. At or above the crash speed (≥ 5) the contact is a **crash** (normal physics: the train is
destroyed and the mission fails with a warning). If the train is **already pressed** (against the
vehicle or the buffer), the order completes in place: the real contact is at speed 0 (the event
reports that speed, not the ordered one), so ordering a high speed does not invent a crash.
**Nothing auto-reverses**: if the destination is only reachable in the opposite sense, the order is
**rejected with a warning** ("no route from the current sense") and you must turn the train by hand
(`train N invert;` or `reverse;`) before repeating it. An order received while the train is
running an itinerary is rejected with a warning (nothing
is paused): use `train N set autopilot false;` or write the maneuver in the itinerary. If the
destination becomes unreachable mid-mission (the route is lost) or the train stays stopped without a
block/schedule/loading reason for about one game hour, the mission fails with a warning. Speed
signals and blocks keep ruling on top: the mission's braking curve only lowers the speed, never
raises it (`stop on contact` does not brake at all: the touch is its goal). A new order replaces the
running mission.

**Naming Elements:**
- `station [ID] set name "My Station";` (the `st` alias works too)
- `sensor [ID] set name "North Sensor";` (the `sn` alias works too)
- `train [ID] set name "Freight Train";` (works as a direct order and inside a trigger block)
- Semaphores and signals **cannot be renamed**: the attempt warns.
- An entity that does not exist warns and changes no name.

### 2. Autopilot and Itineraries
Allows you to program a list of destinations (waypoints) so the train can find its path using A*.
Itinerary blocks use curly braces `{ }`; the `;` after a waypoint is optional, and the closing `}`
ends the block (a `;` after it is also optional; the console adds one automatically when you type
the block). A waypoint can also carry a timetable: `arrival HH` / `arrival H:MM` and/or
`departure HH` / `departure H:MM` (24 h clock; `9` means 09:00 and `09:20` is twenty past nine).

**Create Itinerary:**
```letrain
create itinerary "CoalRoute" {
    add station 1, load
    add station 2, arrival 10:23, reverse, unload, departure 10:30
    add sensor 5, speed 20
}
```
*Waypoint rules:*

- **At least two waypoints**: an itinerary is a loop, so a plan with a single waypoint is rejected with a warning and never assigned (the autopilot stays off). For a single destination use a loose `stop at …` order (or a waypoint action inside a service); repeating the same station is allowed.
- **Known destinations**: if a station/sensor of a waypoint (or the target of a `stop at …` / `stop at sensor …` mission inside a waypoint) does not exist, the itinerary is **not created**, with a warning: a plan missing a stop never runs. A `stop at sensor N` whose id belongs to a station or a speed signal also warns (it is not a plain sensor).
- **Commas are mandatory**: every plan item is separated by commas, and when the waypoint has a plan the **reference (and its optional direction) is also separated by a comma** from the first item: `add station 1 ne, arrival 9:00, uncouple backward all, departure 10:30;`. Without a plan the bare form is unchanged: `add station 1;`. The old comma-less form is not accepted.
- The **order is mandatory**: `arrival` first, then the actions in execution order (`load`, `unload`, `reverse`/`invert`, `stop`, `park`, `wait [NUM]`, `speed [NUM]`, `uncouple forward|backward [NUM|all]`, `couple forward|backward [NUM|all]`, `stop at station|sensor [REF] [speed NUM]`, `stop at end [speed NUM]`, `stop when blocked [speed NUM]`, `stop on contact [speed NUM]`, `fork [ID] set straight|curved|<dir>|flip`), and `departure` last. Writing an attribute out of order is a syntax error.
- **Out-of-range times**: a bare hour outside `0..23` (`arrival 25`) rejects the itinerary with a warning; a full time outside range (`25:00`, `9:60`) is a plain syntax error.
- **Maneuvers**: movement orders (`stop at …`, `stop when blocked …`, `stop on contact …`) are **missions** that run when the waypoint is reached and must complete before the next action: the train drives and ends stopped. `stop at` uses the current sense and **does not auto-reverse either** (nothing auto-reverses): write the `reverse`/`invert` you need or the order is rejected with a "no route from the current sense" warning. `stop on contact` does not auto-reverse either: it drives until touching the vehicle ahead at the ordered speed and stays pressed against it, ready for the `couple` of the next action (touching at crash speed is a real crash and the maneuver fails with a warning). A rejected maneuver **aborts the remaining actions of that waypoint** (the departure and the route to the next waypoint still run) so the choreography never continues in a wrong state. Fork actions force or prepare a switch with the same mapping as the console and triggers; the autopilot keeps orienting the switches along the route it computes. The `departure` releases once the maneuver is done (if it ends late, the train leaves late and the delay is measured); afterwards the route to the next waypoint is recalculated from where the train ended up.
- **Shunting and blocks**: a maneuver whose destination is inside a blocked canton **whose other occupants are all loco-less** (e.g. the wagons it just detached: coupling to them, or approaching them with `stop on contact`) may enter that canton like a manual shunting move; the physical checks still stop the train before any vehicle. A canton held by an unrelated train (with a locomotive) keeps the block: the maneuver waits at the boundary and resumes when it is released. The exemption applies the same to the loose `stop on contact` order (the console/script run-around).
- **Plan cruise after the actions**: the waypoint missions and maneuvers are not the owners of the plan's cruise. When the actions end and the waypoint has no `departure` (or it is already due), the train **resumes the programmed speed** and follows the plan to the next waypoint. Deliberate states win: a `park` without a later departure stays parked and a block wait keeps waiting.
- **`uncouple` direction**: `uncouple forward` detaches at the **head side** of the train and `uncouple backward` at the tail. With the locomotive leading and pulling the wagons, the wagons are behind: the run-around is written `uncouple backward 1` (with no count it detaches every vehicle on that side, like `couple`).
- `arrival` is measured when the waypoint is reached; `departure` is the scheduled leaving time: on arrival the actions run, the train waits until that time and a scheduled departure starts the engine. The dwell is `departure − arrival` in game time. Times are read in sequence: a smaller time than the previous one belongs to the next day (`arrival 23:50, departure 00:10`). If the train arrives late it leaves immediately and the deviation is measured. Without times, a waypoint behaves exactly as before.
- `park` brakes, switches the engine off and **keeps the autopilot running** (unlike `stop`, which brakes and turns the autopilot off). It also exists as a direct order (`train N park;`). The next scheduled departure starts the engine and resumes the cruise speed, so a repeating daily service can end with `park` and leave again the next morning. A `park` with **no later scheduled departure** simply leaves the train parked (engine off, plan kept): it will not move again until a scheduled departure starts it or you drive it manually.
- The train reports its punctuality in `info train N`: arrival and departure deltas per stop in game minutes (`+` = late, `−` = early), plus the current, average and maximum deviation. A train without times shows nothing.
- Safety wins: retention never overrides blocks; if the next block is occupied, the train waits and the delay shows up in the next measurement.
- Times are validated, saved and exported.
- The old comma-less syntax (e.g. `add station 2 reverse unload`) is **rejected everywhere** (game, editor and `letrain-check`) with a diagnostic: this beta does not migrate old itineraries.

**Assign and Activate:**
- `assign itinerary "CoalRoute" to train 1;`
- `train 1 set autopilot true;`
- From the **console** the definitions live in the session: `create` the itinerary in one line and `assign` it in another (or both in the same line). The registry belongs to the current world, so an undo/load/scenario replay that swaps the world starts empty. Only itineraries assigned to a train are stored in the savegame; a definition that was never assigned is a session artifact. A new `create` replaces the previous definition of a name **only when it is accepted**: a rejected `create` (unknown destination, out-of-range time, fewer than two waypoints) **retires** the previous definition, so a later `assign` warns `Itinerary 'x' not found` instead of silently assigning a stale plan.

### 3. Event-Triggered Automation (Triggers)
Responds to game events in real-time.

**Base Structure:**
```letrain
[SELECTOR] on [EVENT] {
    [ACTION];
    [ACTION];
}
```

**Selectors:**
- `sensor [ID|"name"]`, `fork [ID]`, `semaphore [ID]`, `station [ID|"name"]`, `train [ID]` (or generic `train`).
- Only stations and sensors have names: `fork`, `semaphore` and `signal` selectors stay numeric. A named selector is resolved when the trigger is registered; a wrong/unknown name warns and the trigger is not installed.

**Events:**
- Trains: `on train enter`, `on train exit` (optionally with direction `forward`/`backward`).
- Train (any sensor the train steps on): `train [ID] on enter` / `train [ID] on exit`.
- Accidents: `train 1 on crash`, `train on contact forward`.

**Special actions inside blocks (must end with `;`):**
- *Semaphores:* `semaphore [ID] open;` / `semaphore [ID] close|closed;` / `semaphore [ID] set open|closed;` / `semaphore [ID] invert;`
- *Forks:* `fork [ID] set straight;` / `fork [ID] set curved;` / `fork [ID] set <dir>;` / `fork [ID] set flip;` / `fork [ID] flip;` (same behaviour as the console and waypoints: the direction maps to the route leaving towards it and, if there is none, it warns)
- *Conditional Train:* You can use `train at station [ID|"name"]`, `train at sensor [ID|"name"]`, `train at fork [ID]`, or `train at semaphore [ID]` instead of a fixed train number to apply actions to the specific train that triggered the event or is located there. The place is resolved when the action runs; an unknown name warns and nothing runs.
- If a trigger selector (sensor/fork/semaphore/station) does not exist when it is registered, the trigger warns and is not installed.

**Comments:** `#` starts a line comment anywhere in the language (console, `program { … }`, triggers and scenarios). Everything after the `#` up to the end of the line is ignored.

### 4. Game & Editor Commands (Console)
You can type these commands directly into the CLI to manage the game state, cursor, and files.

**Game State & Files:**
- `save [filename];` / `load [filename];` - Save or load a map (a savegame: the current *state*). If the name is a reserved word (e.g. `speed`, `train`, `station`), quote it: `save "speed";`.
- `export [filename];` / `import [filename];` - Export or import a scenario (a `.ltr` *recipe*; see **[scenarios.md](scenarios.md)**).
- `quit;` or `q` - Exit the game.

**Information & Help:**
- `help;` - Show every command group (`CONSOLE`, `BUILD`, `PROGRAM`, `CONFIG`). Filter it with `help console;`, `help build;`, `help program;`, `help config;`, or a single topic such as `help ls;`.
- `version;` - Report the version of the **running build** (e.g. `LeTrain v1.0.0`), the same one shown in the window title. It is data output: it opens the message panel and is **not** recorded in the journal. Use it to check you are not testing a stale copy of the game.
- `ls;` - List every entity. `ls [entityType];` lists one type (e.g. `ls station;`).
- `info;` - Show an overview of the whole world. `info [entityType];` lists that type; `info [entityType] [ID|name];` details a single entity. `info [ID];` without a type warns that the number is ignored (name the type: `info train 5;`).

**Game Clock:**
- `time;` - Report the current game day and time (`Día 1 08:00`). It is data output: it opens the message panel and is **not** recorded in the journal.
- `time set [HH];` - Set the hour on the current day; minutes become `00` (e.g. `time set 9;` = 09:00).
- `time set [HH:MM];` - Set hour and minute in 24 h format; single-digit hours (and minutes) are accepted (`time set 9:05;` and `time set 9:5;` both mean 09:05).
- Valid range is `00:00`–`23:59`. Out-of-range values (`time set 25;`, `time set 25:99;`) warn `Invalid time … (expected 00:00..23:59); the clock is unchanged` and **leave the clock untouched**. A valid change is silent: the new time is already visible in the HUD clock.

**Deletion:**
- `del [entityType] [ID];` - Delete specific infrastructure (e.g., `del station 1;`). *Note: Cannot be used for trains.*
- `clear train [ID];` - Delete a specific train from the map (e.g., `clear train 1;`). *Note: CLEAR is exclusively for vehicles.*

**Cursor Movement & Marks:**
- `go [NUM], [NUM];` - Move cursor to absolute X, Y coordinates.
- `go [entityType] [ID];` - Jump cursor to an entity (e.g., `go station 1;`).
- `go next [entityType];` / `go prev [entityType];` - Cycle cursor through entities.
- `mark [ID];` or `m [ID];` - Save the current cursor position to a mark.
- `go mark [ID];` or `go m [ID];` - Jump cursor to a previously saved mark.
- `face [DIR];` - Turn cursor to face a direction (`n`, `s`, `e`, `w`, `ne`, `nw`, `se`, `sw`).

**Infrastructure Actions (Direct):**
You can directly command infrastructure outside of triggers:
- `semaphore [ID] set open;` / `semaphore [ID] set closed;` / `semaphore [ID] invert;`
- `fork [ID] set straight;` / `fork [ID] set curved;` / `fork [ID] set <dir>;` / `fork [ID] set flip;` / `fork [ID] flip;`
- `signal [ID] set limit [NUM];` / `signal [ID] set mode (max|min);` / `signal [ID] invert;`
- `station [ID] invert;` - Flip a station to face the opposite direction along the rail (platform side switches, same as Space in STATIONS mode).
- `sensor [ID] invert;` - Flip a plain sensor's detection direction (same as Space in SENSORS mode).

**Moving Track Elements (Slide):**
Move a station/sensor/semaphore/speed signal one or more resting cells along the rail, in the direction the element itself is facing. Equivalent to Shift+Arrow in the edit modes.
- `slide station [ID] fw [N];` / `slide station [ID] bw [N];` - Move a station forward/backward.
- `slide sensor [ID] fw [N];` / `slide sensor [ID] bw [N];` - Move a sensor.
- `slide semaphore [ID] fw [N];` / `slide semaphore [ID] bw [N];` - Move a semaphore.
- `slide signal [ID] fw [N];` / `slide signal [ID] bw [N];` - Move a speed signal.

By default it moves one cell forward. If the element is blocked or at the end of the line, the command reports an error.

**Editing Journal, Undo & Redo (While recording):**
While **recording** is on, every edit is recorded in the command journal so it can be undone and redone deterministically. Recording is toggled with the **`R`** key.
- `journal;` - Show the recording state and the list of journaled commands (in order) — what an export would replay.
- `undo;` / `undo [N];` - Undo the last N edits (1 by default). The **`u`** key does the same.
- `redo;` / `redo [N];` - Redo the last N undone edits. **Ctrl+R** does the same.

**Turtle Mode (Scripted Building):**
You can use `write`, `move`, `del`, or `clear` to script sequential cursor movements and track construction.
- `write 5, r, 5, l, 10;` - Draw tracks: advance 5, turn right, advance 5, turn left, advance 10.
- Steps with a name or mark (`write 1, m name;`, `write 1, mark name;`) and bare identifiers are **not implemented yet**: the console warns that the step is ignored (numeric steps and `l`/`r` do work).

---

### Complete Example

```letrain
# Name the stations and the entry sensor
station 1 set name "Central Mine";
station 2 set name "Harbor";
sensor 4 set name "Approach";

# Create the train route
create itinerary "MainRoute" {
    add station "Central Mine", load
    add station "Harbor", unload
}

# Activate the route
assign itinerary "MainRoute" to train 1;
train 1 set autopilot true;

# Automate the junction for any train stepping on the sensor
sensor "Approach" on train enter {
    fork 2 set straight;
    semaphore 1 set open;
}
```

---
<div align="center" style="margin-top: 40px; margin-bottom: 40px;">
  <img src="https://img.shields.io/badge/Java-17%2B-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 17+">
  <img src="https://img.shields.io/badge/LibGDX-Engine-E3363E?style=flat-square&logo=libgdx&logoColor=white" alt="LibGDX">
  <img src="https://img.shields.io/badge/Open_Source-%E2%9D%A4%EF%B8%8F-2EA44F?style=flat-square" alt="Open Source">
  <br><br>
  <strong>The Letter Train Simulator (LeTrain)</strong><br>
  Developed with ☕ by <a href="https://github.com/antoniovazquezaraujo">Antonio Vázquez Araújo</a><br><br>
  <a href="https://github.com/antoniovazquezaraujo/LeTrain/issues">Report a Bug</a> &nbsp;|&nbsp; 
  <a href="https://github.com/antoniovazquezaraujo/LeTrain">Source Code</a> &nbsp;|&nbsp; 
  <a href="mailto:antoniovazquezaraujo@gmail.com">Contact (Email)</a>
</div>
