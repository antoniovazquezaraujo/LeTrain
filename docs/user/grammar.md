# LeTrain - Scripting Grammar (Automation)

LeTrain includes its own lexer/parser (based on ANTLR4) that allows you to automate the railway network using a domain-specific language. Scripts are executed line by line.

## ⚙️ Language Structure

The language supports three main types of statements: direct commands, itinerary creation/assignment (Autopilot), and event-triggered blocks (*triggers*).

### 1. Direct Commands
These are executed immediately. **They require a semicolon (`;`) at the end**.

**Train Actions (`trainRef` can be a number or a name in quotes):**
- `train [ID] accelerate;`
- `train [ID] decelerate;`
- `train [ID] set speed [NUM];` or `train [ID] set [NUM];`
- `train [ID] invert;`
- `train [ID] set engine on;` / `train [ID] set engine off;`
- `train [ID] set forward;` / `train [ID] set backward;`
- `train [ID] load;`
- `train [ID] unload;`
- `train [ID] couple forward [NUM];` / `train [ID] uncouple backward;`

**Naming Elements:**
- `station [ID] set name "My Station";`
- `sensor [ID] set name "North Sensor";`
- `train [ID] set name "Freight Train";`

### 2. Autopilot and Itineraries
Allows you to program a list of destinations (waypoints) so the train can find its path using A*.
Itinerary blocks use curly braces `{ }` and do not require a `;` at the end.

**Create Itinerary:**
```letrain
create itinerary "CoalRoute" {
    add station 1 load
    add station 2 reverse unload
    add sensor 5 forward speed 20
}
```
*Allowed Waypoint Actions:* `load`, `unload`, `reverse`, `stop`, `wait [NUM]`, `speed [NUM]`. You can optionally set the entry direction (`forward` / `backward`) and chain multiple actions.

**Assign and Activate:**
- `assign itinerary "CoalRoute" to train 1;`
- `train 1 set autopilot true;`

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
- `sensor [ID]`, `fork [ID]`, `semaphore [ID]`, `station [ID]`, `train [ID]` (or generic `train`).

**Events:**
- Trains: `on train enter`, `on train exit`, `on train couple`, `on train uncouple` (optionally with direction `forward`/`backward`).
- Accidents: `train 1 on crash`, `train on contact forward`.

**Special actions inside blocks (must end with `;`):**
- *Semaphores:* `semaphore [ID] set open;` / `semaphore [ID] set closed;`
- *Forks:* `fork [ID] set flip;` / `fork [ID] set straight;` / `fork [ID] set curved;`
- *Conditional Train:* You can use `train at station [ID]`, `train at sensor [ID]`, `train at fork [ID]`, or `train at semaphore [ID]` instead of a fixed train number to apply actions to the specific train that triggered the event or is located there.

### 4. Game & Editor Commands (Console)
You can type these commands directly into the CLI to manage the game state, cursor, and files.

**Game State & Files:**
- `save [filename];` / `load [filename];` - Save or load a map.
- `quit;` or `q` - Exit the game.

**Information & Deletion:**
- `ls [entityType];` - List entities of a type (e.g., `ls train;`, `ls station;`).
- `info [entityType] [ID];` - Get details of a specific entity.
- `del [entityType] [ID];` - Delete specific infrastructure (e.g., `del station 1;`). *Note: Cannot be used for trains.*
- `clear train [ID];` - Delete a specific train from the map (e.g., `clear train 1;`). *Note: CLEAR is exclusively for vehicles.*

**Cursor Movement & Marks:**
- `go [NUM], [NUM];` - Move cursor to absolute X, Y coordinates.
- `go [entityType] [ID];` - Jump cursor to an entity (e.g., `go station 1;`).
- `go next [entityType];` / `go prev [entityType];` - Cycle cursor through entities.
- `mark [ID];` or `m [ID];` - Save the current cursor position to a mark.
- `go mark [ID];` or `go m [ID];` - Jump cursor to a previously saved mark.
- `face [DIR];` - Turn cursor to face a direction (`dir_n`, `dir_s`, `dir_e`, `dir_w`, etc.).

**Infrastructure Actions (Direct):**
You can directly command infrastructure outside of triggers:
- `semaphore [ID] set open;` / `semaphore [ID] set closed;` / `semaphore [ID] invert;`
- `fork [ID] set straight;` / `fork [ID] set curved;` / `fork [ID] flip;`
- `signal [ID] set limit [NUM];` / `signal [ID] set mode (max|min);` / `signal [ID] invert;`

**Turtle Mode (Scripted Building):**
You can use `write`, `move`, `del`, or `clear` to script sequential cursor movements and track construction.
- `write 5, r, 5, l, 10;` - Draw tracks: advance 5, turn right, advance 5, turn left, advance 10.

---

### Complete Example

```letrain
// Name the station
station 1 set name "Central Mine";

// Create the train route
create itinerary "MainRoute" {
    add station 1 forward load
    add station 2 backward unload
}

// Activate the route
assign itinerary "MainRoute" to train 1;
train 1 set autopilot true;

// Automate the junction for any train stepping on the sensor
sensor 4 on train enter {
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
