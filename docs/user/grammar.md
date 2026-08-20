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
- `train [ID] stop;`
- `train [ID] invert;`
- `train [ID] set forward;` / `train [ID] set backward;`
- `train [ID] load;`
- `train [ID] unload;`
- `train [ID] link forward [NUM];` / `train [ID] unlink backward;`

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
- Trains: `on train enter`, `on train exit`, `on train link`, `on train unlink` (optionally with direction `forward`/`backward`).
- Accidents: `train 1 on crash`, `train on contact forward`.

**Special actions inside blocks (must end with `;`):**
- *Semaphores:* `semaphore [ID] set open;` / `semaphore [ID] set closed;`
- *Forks:* `fork [ID] set flip;` / `fork [ID] set straight;` / `fork [ID] set curved;`
- *Conditional Train:* You can use `train at station [ID]` instead of a fixed train number to apply actions to the specific train that triggered the event.

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
