# LeTrain - User Manual

Welcome to the official **LeTrain** manual. Here you will find everything you need to know to operate your railway network, manage the economy, and automate your trains, whether using the classic 2D terminal client or the modern 3D view.

## 🎥 Videotutorial / Gameplay

<iframe width="1120" height="630" src="https://www.youtube.com/embed/VwS9Gbu3ygw" title="LeTrain Trailer" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>

---

## 🎮 Basic Controls

The game can be controlled via the keyboard in both its 2D and 3D versions. Below are the main keys and modes:

### Navigation and Views
- **Arrow Keys (or h, j, k, l)**: Move the cursor freely or follow a track (Vim-style navigation).
- **Alt + Arrows (or Alt + h, j, k, l)**: Zoom and pan/orbit the camera.
- **Numbers (0-9)**: Enter a numeric multiplier (quantifier) to increase the speed and jump distance of the cursor. Pressing numbers overrides the current multiplier. Press **Spacebar** to return to moving 1 by 1.
- **Key 'a'**: Enter **Add** mode. This allows you to quickly add infrastructure:
  - **n**: Build Station
  - **e**: Build Sensor
  - **s**: Build Semaphore
  - **g**: Build Speed Signal
- **Key 'o'**: Instantly relocate the cursor to the currently selected train, station, fork, sensor, or speed signal. It always fires a short **locate ping** (an expanding ring) at the cursor, so if nothing is selected — for example in Rails mode — pressing **o** still marks where the cursor is and makes it easy to spot.
- **Key 'z'**: In 3D: Toggle between the three cameras (Perspective, Top-down, and Cab view). In 2D: Cycle the camera deadzone box.
- **Esc**: In 3D: Exit the game menu.
- **Key 'Z' (Shift+z)**: In 2D: Toggle camera pagination mode.
- **Key 'Tab'**: Cycle through the information bar visibility levels (Compact, Full, Hidden).

### Game Modes & Shortcuts
LeTrain is deeply modal. Pressing the following keys will switch your current interaction mode:
- **`r`**: **Rails Mode** (Default) - Build and delete tracks.
- **`a`**: **Add Mode** - Add infrastructure (Stations, Sensors, Semaphores).
- **`t`**: **Trains Mode** - Build new trains.
- **`d`**: **Drive Mode** - Drive and control trains.
- **`c`**: **Link Mode** - Couple wagons and locomotives.
- **`u`**: **Unlink Mode** - Uncouple wagons and locomotives.
- **`f`**: **Forks Mode** - Manage and flip track forks.
- **`s`**: **Semaphores Mode** - Manage semaphores.
- **`g`**: **Speed Signals Mode** - Manage speed limit signals.
- **`n`**: **Stations Mode** - Select and inspect stations.
- **`e`**: **Sensors Mode** - Select and inspect sensors.
- **`p`**: **Program Mode** - Open the IDE to write automation scripts.
- **`:`**: **CLI Mode** - Open the command line interface.
- **`R`**: **Toggle Record/edit mode** (off by default). When enabled, the world (trains, economy, derailments) freezes while you are in any editing mode (Rails, Add, Stations, Sensors, Semaphores, Speed Signals, Forks, Trains, console or program editor), track construction becomes instantaneous, and every edit is journaled for deterministic undo and scenario export. Simulation only runs in play/view modes (Drive, Menu, Link/Unlink) or when the toggle is off.

### Construction (Rails Mode)
- **Shift + Arrows (or H, J, K, L)**: Build new tracks.
- **Ctrl + Arrows (or Ctrl + h, j, k, l)**: Delete existing tracks.
- **Home**: Create a semaphore on the track.
- **Insert**: Create a sensor on the track.
- **End**: Create a station.
- **Del**: Create a speed limit signal on the track.

### Moving Track Elements
In the **Stations** (`n`), **Sensors** (`e`), **Semaphores** (`s`) and **Speed Signals** (`g`) modes you can slide the selected element along the rail, in the direction the element itself is facing:
- **Shift + Up / Shift + k**: move the element one slot forward along the track.
- **Shift + Down / Shift + j**: move the element one slot backward.

The element follows the rail and crosses forks (using their active branch), skips positions occupied by other elements, never jumps over a train, and stops at the end of the line. Use the **Spacebar** to invert the facing direction of the selected element (in **Stations** mode, Spacebar inverts the station when no train is stopped on it; otherwise it starts the load/unload action). If a load/unload station is moved away from its loading zone and loses its influence, it becomes a generic station; an itinerary that asks that station to load or unload will then be passed through.

### Trains and Driving (Drive / Trains Mode)
- **Trains Mode**: Lowercase letters create wagons (keys 1, 2, 3 for cargo type). Uppercase letters create locomotives (keys 0-9 for color). Press **Enter** to finish.
- **Drive Mode**: Left/Right arrows select a train; if none was selected when you entered, the first available train is selected for you. Up/Down arrows accelerate/brake. **Spacebar** reverses direction (only when stopped).
- **Link / Unlink Mode**: Up/Down selects the end of the train. Left/Right selects the amount of wagons. **Spacebar** executes coupling/uncoupling.


### Command Line Interface (CLI) Mode
Press the `:` key to open the integrated console (similar to Vim). From here you can type direct commands to build tracks, spawn trains, or manipulate entities instantly.
Some useful commands:
- `go 10, 5;` - Move the cursor to an absolute coordinate.
- `new st;` - Build a station under the cursor.
- `new loco A red;` - Spawn a red locomotive 'A'.
- `train 1 set engine on;` - Start train 1's engine.
- `ls st;` - List all stations.
- `quit;` or `q` - Exit the game.

For a complete reference of the CLI commands, see **[grammar.md](grammar.md)**.

### Scenario Editor and Undo/Redo
- **`p`**: Open the **LeTrain Editor** (scenario editor). It has three tabs — **Scenario**
  (`seed` + `on build` + `on start`), **Program** (the automation script) and **Config** (game
  settings) — a per-tab quick reference, and a footer with `Export`/`Import` of `.ltr` scenario
  files, `Refresh`, `Reprogram` and `Rebuild`. `Esc` closes it. See **[scenarios.md](scenarios.md)**.
- **Record/edit mode (`R`)**: while it is on, **`u`** undoes and **Ctrl+R** redoes edits (the same
  as `undo;` / `redo;`). Outside this mode, `u` is the **Unlink** mode.

### Interaction
- **Spacebar**: In Semaphores, Speed Signals, or Sensors mode, invert the direction of the device.
- **Key 'm'**: In Semaphores mode, change the state (green/red). In Speed Signals mode, toggle the type of signal (Max/Min). In Trains mode, start/stop the engine.
- **Enter**: Start loading/unloading cargo when stopping a train at a station.

## 🏭 Station Types and Cargo

In LeTrain, logistics are the key to the economy. The map generates natural resource points procedurally, and your job is to connect them:

- **Load Stations (`▲`)**: Built adjacent to producing zones (e.g., coal, gold, or ruby mines). They extract material into your trains.
- **Unload Stations (`▼`)**: Built next to consuming zones (e.g., cities or factories). Here you sell your cargo and earn profits.
- **Generic Stations (`◇`)**: Act as waypoints or exchange points, and can be manually assigned.

*(Visual note: When playing in the 2D Terminal, tracks are rendered using continuous ASCII characters, and dead ends are marked with yellow icons. Additionally, you will see a blinking underline on the train while it is loading or unloading cargo, which will become solid when completed).*

## 💰 Economy and Profits

You start with your account at zero. To earn money you must:
1. Build tracks from a producing zone to a consuming zone.
2. Build a train and its wagons (beware of fuel costs!).
3. Transport the cargo. **Profit is calculated based on the distance traveled**: the longer the journey from the producing zone to the consuming zone, the higher the payout!

You can adjust the base costs and rewards by modifying the `letrain.cfg` file. It ships next to the
game; a `letrain.cfg` in the working directory overrides the packaged one. Besides costs, the file
holds other settings, such as `ui.highlightBlockedTracks=true` (the default), which tints
reserved/blocked tracks with the owning train's livery in both clients; set it to `false` to keep
the normal rail colour.

## 🕐 Game Time & Day/Night

The world runs on a logical game clock, independent from real time. The HUD shows the current game date and time (`D1 08:00`) in both the 2D terminal and the 3D view, and the world starts on day 1 at 08:00; one full game day lasts 24 real minutes by default (adjustable in `letrain.cfg`). The clock is deterministic and drives the simulation, so the same scenario always replays with the same times.

- **Day/Night cycle**: Sunrise and sunset are simulated, and the world palette shifts through day, dusk and night in both clients: ambient light, sunlight, sky and terrain colours in 3D, and the terminal colour palette in 2D. The same network looks very different at midnight.
- **Headlights**: Locomotive headlights only shine once it gets dark, and only while the engine is running — switching the engine off turns them off. The two lamps stay visible (unlit) at any hour, and only the head locomotive of a consist shines: the other locomotives keep their lamps off.
- **Clock commands** (full syntax in **[grammar.md](grammar.md)**):
  - `time;` - Show the current game date and time.
  - `time set HH[:MM];` - Jump the clock, e.g. `time set 21:30;` or `time set 9;` for 09:00. Out-of-range times are rejected with a warning and the clock does not move.
- **Itinerary schedules use this clock**: The `arrival` and `departure` times of a waypoint are measured against the game clock, so `time set` is the quick way to test a timetable without waiting for the day to pass. See **Autopilot and Routes** below, **[grammar.md](grammar.md)** and **[scenarios.md](scenarios.md)**.

## 🤖 Autopilot and Routes

To manage dozens of trains without going crazy, LeTrain includes an Autopilot that you can program yourself. Check the complete programming guide at: **[grammar.md](grammar.md)**.

Itineraries also support **timetables and shunting maneuvers**: each waypoint can carry an `arrival` and/or `departure` time (24 h clock). On arrival the waypoint actions run, the train **holds until its departure** and then leaves; if the actions finish late, it leaves late and the delay is recorded. A repeating daily service can end with `park` (brakes, engine off, autopilot still armed) and start again by itself on its next scheduled departure. Punctuality is reported per stop in `info train N`: arrival/departure deviation in game minutes, plus current, average and maximum deviation. Waypoints can chain maneuvers as well, including `uncouple`/`couple … all` and `stop on contact`, for run-arounds and siding moves. See the **Autopilot and Itineraries** section of **[grammar.md](grammar.md)** and the scenario guide **[scenarios.md](scenarios.md)** for the full syntax and examples.

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
