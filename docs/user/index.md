
[🇪🇸 Leer en Español](index_es.md)

<div style="display: flex; justify-content: center; width: 100%; overflow: hidden; margin-top: 20px; margin-bottom: 20px;">
<pre style="font-size: 1.2em; line-height: 1.2; font-weight: bold; background: transparent; border: none; overflow: hidden; padding: 0; text-align: left;">
      __       ______           _
     / /   ___/_  __/________ _(_)___
    / /   / _ \/ / / ___/ __ `/ / __ \
   / /___/  __/ / / /  / /_/ / / / / /
  /_____/\___/_/ /_/   \__,_/_/_/ /_/
       The Letter Train Simulator           
   (C) 2006-2026 Antonio Vazquez Araujo
</pre>
</div>

**LeTrain** is a procedural train simulator that bridges the gap between classic ASCII aesthetics and modern 3D rendering. Originally a small C++ experiment, it has evolved into a fully-fledged Java simulation where players manage rail networks, logistics, and economies in an infinite, procedurally generated world.

## ✨ Features

- **Dual Rendering Engines:**
  - **Modern 3D View:** Built with [LibGDX](https://libgdx.com/), offering a dynamic, fully navigable 3D environment with zoom, orbit, and a comprehensive HUD.
  - **Classic 2D Terminal:** Powered by [Lanterna](https://github.com/mabe02/lanterna), providing a retro, pure-ASCII aesthetic for those who love the classic terminal feel.
  
- **Infinite Procedural World:** The terrain is dynamically generated using Perlin noise, creating endless landscapes to explore and build upon.

- **Advanced Automation & IDE:**
  - Includes a built-in programming language (parsed via ANTLR4) for true automation.
  - Features an integrated development environment (IDE) directly within the game to write, compile, and execute train logistics scripts.
  - Automate switches, set up autopilot routes, and manage complex itineraries.

- **Economy & Logistics:** Build tracks, manage resources, and balance your budget. The central economy system reacts to your decisions.

## 🚀 Quick Start

### Building from Source

LeTrain requires **Java 17+** and **Maven**.

```bash
# Clone the repository
git clone https://github.com/antoniovazquezaraujo/LeTrain.git
cd LeTrain

# Build the project and package it
mvn clean package -DskipTests
```

The build process uses `jpackage` to generate standalone native launchers in the `output/LeTrain` directory. 

### Running the Game

You can run either the modern 3D version or the classic 2D version:

- **3D Engine:** Run the `LeTrain` executable.
- **2D Terminal:** Run the `LeTrain2D` executable.

## 📖 Documentation

- [User Manual](manual.md)
- [Scripting and Automation Grammar](grammar.md)

## 🎥 Media & Tutorials

### Navigation & Camera
- **Basic Navigation:** The cursor moves using the arrow keys across the map. If it enters a track, it automatically follows it, both forwards and backwards.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/7fKXj1krkFk" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/eidXDfMM5Ec" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Camera Zoom and Pan:** You can zoom in and out using Alt and the arrow keys, as well as rotate the camera. The mouse wheel also controls zoom.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/hWSTbSypcNo" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/3djcZgXX7h8" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Map Discover:** As the user navigates across the map, new undiscovered zones are dynamically revealed.<br><div><strong>3D Engine</strong><br><iframe width="1120" height="630" src="https://www.youtube.com/embed/SsmqqXQSOQo" frameborder="0" allowfullscreen></iframe></div>
- **Cursor Movement:** To speed up cursor movement, you can type a number. After that, the cursor will jump in larger distances. To return to moving 1 unit at a time, press the spacebar.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/Xu21CMRjhuw" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/0EMS3kzGMNg" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Cursor Relocation:** When you need to instantly bring the cursor to a train, a station, or a fork, you can press the `o` key.<br><div><strong>3D Engine</strong><br><iframe width="1120" height="630" src="https://www.youtube.com/embed/PUJ00irsU5s" frameborder="0" allowfullscreen></iframe></div>

### Building the Network
- **Rail Creation:** To lay down tracks, use `Shift` and the arrow keys. At the end of the rails, yellow blocks are displayed which disappear when the rails are joined together.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/PnIWZxik3Ds" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/00y-E6YBxIc" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Fork Creation:** To create a fork, simply move along an existing track and exit it. The fork automatically creates the necessary semaphores. It is also created when entering an existing track from the outside. Forks are only created at 45-degree angles and if, upon entering the track, you turn to continue along it. If you go straight through, simple crossings are created instead.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/iVHsKuj0Xm8" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/mYqpJxpBA3k" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Remove Rails:** To remove rails, use the `Ctrl` key with the arrow keys, both forward and backward. The cursor will automatically follow the track as you delete it.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/TDF-26e1wqI" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/L_FrO48OlZY" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Tunnels and Bridges:** When rails are created over water, bridges are built. When rails are created inside mountains, tunnels are excavated. In `Drive` mode, you cannot see inside tunnels, but in `Rails` mode, you can.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/bn8isLuMq8Y" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/_L_J0J8-_m4" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Terrain Types:** There are three distinct terrain types: normal ground (green), water (blue), and mountains (brown). Building rails over water and under mountains is more expensive.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/uf-9XmoXH8E" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/4d81Dt1iTaM" frameborder="0" allowfullscreen></iframe></div>
</div>

### Managing Trains
- **Create Trains:** To create trains, switch to `Trains` mode. In this mode, lowercase letters spawn wagons. Before creating them, you can change their cargo type using numbers: 1 for gold, 2 for coal, and 3 for ruby. Each wagon type can only load that specific cargo. Uppercase letters are used to spawn locomotives. Right after creating a locomotive, you can change its color by typing a number from 0 to 9. It can be changed multiple times. Pressing Enter finishes the train creation process.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/KlsM5MXg8Sg" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/7zhFuPuNfwY" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Select Trains:** In `Drive` mode, you can drive a train, meaning you can accelerate and decelerate. To select the next or previous train, use the left and right arrow keys. The currently selected train displays a green line above it.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/M-osKFq_xgE" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/iZisYx3nR8U" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Move Train:** To move a train in `Drive` mode, use the up and down arrow keys to accelerate and decelerate respectively. The spacebar reverses its direction, but only when it is completely stopped. In the lower left corner of the screen, a speedometer displays one line for the requested speed and another for the actual achieved speed. There is a delay between the two due to the train's inertia.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/_rC-jeLiWSs" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/S8dJ6oAbT3k" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Train Camera View (Cab View):** There are three cameras available: perspective camera, top-down camera, and locomotive camera (Cab View). Cycle through all three using the `c` key.<br><div><strong>3D Engine</strong><br><iframe width="1120" height="630" src="https://www.youtube.com/embed/sCCPhXLhoeo" frameborder="0" allowfullscreen></iframe></div>
- **Link and Unlink:** To couple and uncouple wagons and locomotives, use the `link` and `unlink` modes. In these modes, you first select which end you want to operate from using the up and down arrows. Then, with the left and right keys, you select the amount of vehicles you want to link or unlink. Finally, the spacebar executes the operation. A locomotive cannot push a vehicle unless it is properly coupled to it. These operations can only be performed while the train is stopped.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/StOnossHNFA" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/CK-gKHcVLTE" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Train Crash:** Trains can make contact at low speeds without issue. When the speed is high, simply put: goodbye train.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/9tBCqFEWy74" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/f0ra9mr-pWQ" frameborder="0" allowfullscreen></iframe></div>
</div>

### Logistics & Stations
- **Select Station:** Stations are selected in `stations` mode using the left and right keys.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/W8lSjmPWKv8" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/Z6syPCHnpzA" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Load Unload Stations:** To handle cargo, stations are required. There are three types of loading and unloading zones: gold (yellow), coal (black), and ruby (red). Loading zones are represented as cubes, and unloading zones as rectangles on the ground.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/SsvqDmw31ck" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/gZSVIpeFrf4" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Load Unload Zones:** By building a station close enough to a loading or unloading zone, the train will be able to load or unload cargo, provided it has a wagon matching the zone's cargo type.<br><div><strong>3D Engine</strong><br><iframe width="1120" height="630" src="https://www.youtube.com/embed/ojiHCBCYSj8" frameborder="0" allowfullscreen></iframe></div>
- **Load and Unload Actions:** You can only load or unload when the train is fully stopped. It is done by pressing the `Enter` key. During loading, the station sign blinks, and you can visually observe the wagons being loaded or unloaded.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/y1hJVD04nX8" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/zQh3w74xfB8" frameborder="0" allowfullscreen></iframe></div>
</div>

### Safety & Automation
- **Create Semaphores:** Semaphores are created when the cursor is over the track using the `Home` key. In `semaphores` mode, they are selected with the horizontal arrows and their state is toggled using the spacebar.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/VMJCvCAxExM" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/UiT4tzuuw9A" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Create Sensors:** Sensors allow you to automate tasks, such as stopping a train when it passes over it, changing a fork's direction, or toggling a semaphore. They are created when the cursor is over the track using the `Insert` key.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/twaJryI_fKs" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/0R8XAXsHmt8" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Security (Blocks):** Between each track change, a block (cantón) is created, just like in real-world railways. This represents a zone that a train can reserve so no other train can enter it. When a train moves on autopilot (i.e., when assigned an itinerary), it automatically reserves the block it enters and the upcoming one. If it cannot reserve the next block because it is already occupied, it brakes to avoid crashing. Braking distances must be calculated properly to prevent a train from sliding into an occupied block. If an invasion happens, both trains are automatically braked and forced into manual mode.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/p7L3IR3CbWI" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/26AmqDbMsUk" frameborder="0" allowfullscreen></iframe></div>
</div>
- **Program IDE:** In `program` mode, you have access to a simple IDE that allows you to program actions and events. Itineraries can be created and assigned to trains. An itinerary can contain several stations or sensors, so when the train passes them, it performs specific tasks automatically, such as loading, unloading, reversing direction, or stopping.<br><div style="display: flex; gap: 10px; flex-wrap: wrap;">
  <div style="flex: 1; min-width: 300px;"><strong>3D Engine</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/ke0XuMDk3iM" frameborder="0" allowfullscreen></iframe></div>
  <div style="flex: 1; min-width: 300px;"><strong>2D Terminal</strong><br><iframe width="100%" height="315" src="https://www.youtube.com/embed/z6CGDnxDqa0" frameborder="0" allowfullscreen></iframe></div>
</div>

### Historical Archive (Early Alpha)
Take a look at how LeTrain started in this early 2D terminal emulator recording:

<div><strong>3D Engine</strong><br><iframe width="1120" height="630" src="https://www.youtube.com/embed/2WVScFIG4_E" frameborder="0" allowfullscreen></iframe></div>

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
