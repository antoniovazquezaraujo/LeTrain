[🇪🇸 Leer en Español](cheatsheet_es.md)

# LeTrain - CLI Command Cheat Sheet

---

## 1. Topological and Absolute Navigation

| Command | Description | Example |
| :--- | :--- | :--- |
| `go <x>, <y>;` | Move the cursor to absolute Cartesian coordinates. | `go 10, -5;` |
| `go next <entity>;` | Follow the track forward until finding the entity. | `go next fork;` |
| `go <entity> <id>;` | Teleport the cursor to an entity's location. | `go st "Madrid";` |
| `go prev <entity>;` | Follow the track backward until finding the entity. | `go prev st;` |
| **`gn <entity>;`** | Quick abbreviation for `go next`. | `gn st;` |
| **`gp <entity>;`** | Quick abbreviation for `go prev`. | `gp fk;` |
| `go end;` | Follow the current track until the end of the line. | `go end;` |

> **Entity Abbreviations**: 
> - **`st`** = `station`
> - **`sn`** = `sensor` 
> - **`fk`** = `fork`
> - **`sm`** = `semaphore`
> - **`sg`** = `signal`
> - **`tr`** = `train`
> - **`rl`** = `rail`

> **Command Abbreviations**: 
> - **`go`** = `g`

---

## 2. Bookmarks

| Command | Description | Example |
| :--- | :--- | :--- |
| `mark <name>;` | Save current coordinates under a name or number. | `mark base;` |
| `go mark <name>;` | Teleport the cursor to the bookmark. | `go mark base;` |
| **`g m <name>;`** | Quick abbreviation to jump to a bookmark. | `g m base;` |

> **Bookmark Names**: 
> - If a bookmark name contains spaces, wrap it in double quotes, e.g. `go mark "central station";`

---

## 3. Cursor Heading

| Command | Description | Example |
| :--- | :--- | :--- |
| `face <direction>;` | Turn toward a compass heading (`n, s, e, w, ne, nw, se, sw`). | `face ne;` |
| `face <entity> <id>;` | Rotate cursor heading toward an entity. | `face tr 1;` |
| `face m <string>;` | Rotate cursor heading toward a bookmark. | `face m "madrid";` |

---

## 4. Track Management (Turtle Graphics)

| Base Command | Action | Example |
| :--- | :--- | :--- |
| `write <sequence>;` | Advance while laying new track. | `write 5, l, m base, r, 1;` |
| `move <sequence>;` | Move the cursor without building track. | `move 5, l;` |
| `del <sequence>;` | Advance while **ripping up track** (destroys trains and entities). | `del 3;` |
| `clear <sequence>;` | Advance while removing **only trains and wagons** (preserves tracks). | `clear 10;` |

> **Sequence Elements**: 
> - `<number>`: Number of grid cells to advance straight ahead.
> - **`l`**: Turn left.
> - **`r`**: Turn right.
> - `m <mark_name>`: Automatically navigate toward a bookmark.
> 
> If no distance is specified after a turn, it defaults to 1 step (e.g. the sequence `"r,r,3"` is equivalent to `"r,1,r,3"`).

---

## 5. Creating and Deleting Entities and Vehicles

| Command | Description | Example |
| :--- | :--- | :--- |
| `new st;` / `new sm;` / `new sg;` | Create infrastructure at the cursor position. | `new st;` |
| `new loco <Letter> [color];` | Create a locomotive with the specified letter and optional color. | `new loco Z blue;` |
| `new wagon <Letter> [type];` | Create a wagon with the specified letter and optional cargo type. | `new wagon B ruby;` |
| `del <entity> <id>;` | Delete an entity (stations, semaphores, etc.) leaving the track intact. | `del sm 1;` |
| `clear <entity> <id>;` | Delete a train or wagon by its ID. | `clear tr 1;` |

---

> **Available Colors**:
> `red`, `green`, `blue`, `yellow`, `black`, `white`, `orange`, `purple`, `gray`, `brown`
>
> **Cargo Types**:
> `coal`, `gold`, `ruby`

---

## 6. Direct Infrastructure Control

| Entity | Command | Example |
| :--- | :--- | :--- |
| **Fork** (Switches) | `fork <id> set left;` / `set right;` / `flip;` | `fork 1 flip;` |
| **Semaphore** (Signals) | `semaphore <id> open;` / `close;` / `invert;` | `semaphore 2 invert;` |
| **Speed Signal** (Speed limits) | `signal <id> limit <number>;` <br> `signal <id> set mode max;` / `set mode min;` <br> `signal <id> invert;` | `signal 3 limit 120;` <br> `signal 3 set mode max;` |
| **Station** (Stations) | `station <id> invert;` | `station 1 invert;` |
| **Sensor** | `sensor <id> invert;` | `sensor 2 invert;` |

---

## 7. Direct Train and Vehicle Control

| Base Command | Action | Example |
| :--- | :--- | :--- |
| `train <id> couple <dir> [n];` | Couple wagons in the given direction (`forward`/`fw` or `backward`/`bw`). If `n` is omitted, couples all wagons. | `train 1 couple forward 2;` or `train 1 couple backward;` |
| `train <id> uncouple <dir> [n];` | Uncouple wagons in the given direction (`forward`/`fw` or `backward`/`bw`). | `train 1 uncouple fw 1;` |
| `train <id> set speed <n>;` | Set target train speed. | `train 1 set speed 5;` |
| `train <id> reverse;` | Reverse direction of travel. | `train 1 reverse;` |
| `train <id> set engine on;` / `off;` | Turn locomotive engine on or off. | `train 1 set engine on;` |
| `train <id> set autopilot true;` | Enable autopilot mode. | `train 1 set autopilot true;` |
| `train <id> load;` / `unload;` | Load or unload cargo (must be stopped at a station). | `train 1 load;` |

---

## 8. Saving and Loading

| Command | Description | Example |
| :--- | :--- | :--- |
| `save <name?>;` | Save the map. If omitted, uses an automatic name. | `save "map1";` |
| `load <name?>;` | Load a saved map. | `load "map1";` |

---

## 9. Names and Custom References

Any train or infrastructure element (stations, semaphores, bookmarks, etc.) can be given a custom name so you don't have to remember numeric IDs.

| Command | Description | Example |
| :--- | :--- | :--- |
| `<entity> <id> set name "<name>";` | Assign a name to an entity using its numeric ID. | `train 1 set name "Express";`<br>`st 2 set name "Central";` |
| `<entity> "<old_name>" set name "<new_name>";` | Rename an entity that already has a name. | `train "Express" set name "HighSpeed";` |

Once named, **you can use the name enclosed in quotes** (or without quotes if it has no spaces) in any command expecting an `<id>`:

- `train "HighSpeed" set engine on;`
- `go st "Central";`
- `clear tr "HighSpeed";`
