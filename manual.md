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
- **Key 'o'**: Instantly relocate the cursor to the currently selected train, station, fork, sensor, or speed signal.
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

### Construction (Rails Mode)
- **Shift + Arrows (or H, J, K, L)**: Build new tracks.
- **Ctrl + Arrows (or Ctrl + h, j, k, l)**: Delete existing tracks.
- **Home**: Create a semaphore on the track.
- **Insert**: Create a sensor on the track.
- **End**: Create a station.
- **Del**: Create a speed limit signal on the track.

### Trains and Driving (Drive / Trains Mode)
- **Trains Mode**: Lowercase letters create wagons (keys 1, 2, 3 for cargo type). Uppercase letters create locomotives (keys 0-9 for color). Press **Enter** to finish.
- **Drive Mode**: Left/Right arrows select a train. Up/Down arrows accelerate/brake. **Spacebar** reverses direction (only when stopped).
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

For a complete reference of the CLI commands, check out the developer documentation or type `info` in the console.

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

You can adjust the base costs and rewards by modifying the `economy.properties` file before launching the game.

## 🤖 Autopilot and Routes

To manage dozens of trains without going crazy, LeTrain includes an Autopilot that you can program yourself. Check the complete programming guide at: **[grammar.md](grammar.md)**.

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
