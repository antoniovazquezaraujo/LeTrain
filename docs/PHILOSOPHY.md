# LeTrain Manifesto: Design Philosophy, Aesthetics, and Governing Laws

> **English** | [Español](PHILOSOPHY_es.md)

> *"A procedural logistics simulator where the restraint of ASCII and the integrity of railway engineering meet the freedom of automation."*

---

## 0. Why: The Cardboard Box and the Engineer-Child

LeTrain is, above all, a game for **"engineer-children"** or **"child-engineers"**.

> *"When I was little, I had some poorly designed toys where the only thing you could do was watch the toy 'play by itself'. That ended up boring me and led me to play with the cardboard box it came in, turning it in my imagination into a 10-axle semi-truck hauling heavily across the rug carrying a sneaker-spaceship toward the launch pad.*
>
> *The idea of LeTrain is to let the user unleash their imagination and invent, to develop the infinite possibilities of a programmable system, to share their breakthroughs, to enjoy without feeling overwhelmed by thousands of options for every little thing, nor frustrated by fake mechanisms that only pretend to do something but deep down do nothing at all.*
>
> *In LeTrain, trains are 'real', and they must be brought to their destination. They are not decorative props. The 2D - 3D duality is precisely what forces us to keep it simple, conceptual, and clear."*
> — **Antonio Vázquez Araujo**, Creator of LeTrain

---

## 1. Mission and Essence: Toys That Do Not Play by Themselves

LeTrain was born as an homage to and evolution of classic transport and logistics simulators, conceived through the lens of clean engineering, imagination, and conceptual elegance.

* **Rejection of the toy that plays by itself:** LeTrain is not an interactive screensaver, a passive idle clicker, or an overwhelming checklist of micromanagement. The player is a network designer: if the system works, it is because the player laid the tracks, secured the blocks, or scripted the itinerary.
* **Real trains, not decorative props:** Every train has mass, length, inertia, a reserved track block, and real cargo in its wagons. They are not aesthetic particles orbiting pre-baked loops: they are operational machines that must physically reach their destination.
* **Honest mechanisms versus fake mechanisms:** If a switch flips, a semaphore closes a block, or a station stores coal, that action genuinely happens within the simulation graph. In LeTrain there are no inflated stats or cosmetic facades pretending to simulate non-existent depth.
* **Inviolable red lines:**
  * **Zero predatory mechanics:** There will never be microtransactions, in-app purchases, advertisements, artificial wait times ("wait 2 hours to build this track"), or engagement loops engineered to exploit player psychology.
  * **No artificial cosmetic bloat:** Every visual element on screen maps directly to a model entity. No baroque visuals are added that obscure system readability.
  * **Open source and user sovereignty:** The player and community fully own their experience. Save games, scenarios, and configurations live as plain text: readable, editable, and verifiable.

---

## 2. Aesthetic Duality: Two Dimensions, One Single Core

One of LeTrain's defining characteristics is its visual bicephaly: two completely independent frontends sharing a single, immutable logical core.

> **Design Principle:** *The 2D-3D duality is the anchor that forces us to keep the game simple, conceptual, and clear.* If an idea cannot be expressed with clarity on an ASCII grid, it is almost certainly overdesigned.

### The 2D Terminal (Lanterna)
* **Neither a retro gimmick nor a technical compromise:** It is a first-class citizen. The terminal interface delivers immediacy, blazing performance, and unmatched information density.
* **The dignity of the ASCII character:** Every glyph (`=`, `|`, `/`, `\`, `+`, `#`) carries spatial weight and unmistakable functional meaning. It evokes the austere, rigorous atmosphere of Centralized Traffic Control (CTC) consoles from classic railway dispatch centers.

### The Dynamic 3D Engine (LibGDX)
* **Glyphs as spatial sculpture:** The 3D engine does not replace ASCII art; it projects it physically into three dimensions. Characters become blocks and tracks suspended over an infinite landscape carved by Perlin noise.
* **Absolute functional symmetry:** There will never be an action possible in 3D that cannot be performed in 2D, and vice versa. What happens in one dimension is mathematically identical to what happens in the other. Both views are mere visitors (`Visitor pattern`) observing the same underlying model.

### Keyboard Ergonomics and the Nod to "Old-School Hackers" (Vim & Console)
LeTrain pays explicit homage to the culture of classic terminal hackers, legendary text editors (`vi/vim`), seminal roguelikes, and Unix environments where hands never need to leave the keyboard's home row.

* **Natural navigation with `h, j, k, l`:** Moving the cursor across the map, following tracks, or panning the view requires neither reaching for the mouse nor hunting for arrow keys. Navigation becomes seamless muscle memory.
* **Console mode and speed of thought:** Alongside keyboard shortcuts, the built-in console allows querying state, toggling switches, or commanding trains in a few keystrokes, avoiding clunky floating menus or modal dialogues.
* **Immersion and Flow State:** This is not mere nostalgia; it is ergonomic discipline. By removing mouse friction, the player enters an uninterrupted state of deep concentration (*flow*), manipulating the railway network at the speed of thought.

---

## 3. Physical Laws and the Mechanical Engine

LeTrain's backend is governed by software engineering principles that ensure large-scale stability and operational fidelity.

* **Event-Driven Reactivity vs. Blind Polling:**
  * *The Sacred Loop Rule:* No component may blindly poll all tracks, cars, or elements every game tick to check collisions or reserve blocks.
  * Decisions happen at boundaries: when the train head enters a switch or crosses a sensor, blocks are acquired; when the tail clears the node, blocks are released. This reactivity allows simulating massive networks with negligible CPU overhead.
* **The Truth of the Railway (Blocks and Signals):**
  * Train safety never relies on teleportation hacks or vehicles ghosting through one another. The block and topological graph system (`RailwayGraph`, `BlockManager`) is the unyielding law that prevents disaster.
* **Fair and Comprehensible Consequences:**
  * If a train derails from excessive curve speed or two convoys collide due to a missing signal, the cause is transparent and traceable. Failure in LeTrain is educational: it teaches the player to become a better engineer.

---

## 4. Automation Philosophy: The Player as Programmer

LeTrain elevates logistics by empowering the player with computational tools right on the rails.

* **A Dedicated Domain-Specific Language (ANTLR DSL):**
  * Rather than relying solely on graphical menus, LeTrain provides a console and a declarative scripting language to define routes, itineraries, speeds, and autopilot logic.
* **Determinism and Plain-Text Scenarios:**
  * A random seed plus an `.ltr` recipe reconstructs the exact same railway universe across any machine, operating system, or era.
  * Scenarios require no opaque binary dumps: they are plain-text files, versionable in Git, and headlessly verifiable with tools like `letrain-check`.
* **Freedom to Experiment Fearlessly:**
  * The `Record / Undo / Redo` journaling system and live snapshots exist to encourage bold experimentation. Players can test daring track layouts and instantly rewind time if the result fails.

---

## 5. Quality and Robustness Contract

A system intended to endure must be built to uncompromising professional standards.

* **Zero Tolerance for Ghost Bugs:**
  * Collisions, deadlocks, or cargo anomalies must always be explainable by game rules, never by race conditions, threading desyncs, or corrupted state.
* **Decoupled Architecture (MVP):**
  * The Model (`Model`) is the source of truth and knows nothing of Presenters or Views. No UI or rendering dependencies (Lanterna, LibGDX, OpenGL) may ever leak into the game engine (`core`).
* **The Safety Net of Testing:**
  * Every core mechanic, physical rule, command grammar rule, and economy calculation is verified through automated unit and integration tests (`JUnit 5`, `Mockito`). A change that breaks tests or diminishes coverage is not ready.

---

## 6. Custodian Mandate for Future Maintainers

If you are reading this document with the intent to continue, maintain, or evolve LeTrain in the absence of its original creator, you accept this pledge:

1. **The Cardboard Box Test:** Before introducing any new mechanic, ask yourself: *Are we giving the player a tool to invent, or a toy that plays by itself? Is it an honest mechanism or a cardboard facade?*
2. **Preserve Identity:** If a feature belongs in an overly baroque simulator or a casual clicker, it does not belong in LeTrain. Respect the restraint, dignity, and clarity of the dispatch desk.
3. **Respect Symmetry:** Maintain strict parity between the 2D terminal client and the 3D graphical client. If a feature cannot be elegantly expressed in both dimensions, rethink it.
4. **Guarantee Keyboard Sovereignty (Keyboard-First):** Never turn LeTrain into a mouse-dependent game. 100% of actions, navigation, and commands must remain fluidly executable via keyboard with Vim keys and console. The mouse may be an optional convenience, never a requirement.
5. **Prioritize Simplicity over Cleverness:** Favor clean, maintainable, modular code over premature optimization or hollow abstractions.
6. **Document Architectural Decisions:** Every significant architectural choice must be recorded in an ADR (`docs/developer/adr/`). Architecture is the map that keeps future travelers from getting lost.
7. **Leave the Workshop Cleaner than You Found It:** Delete merged branches, remove temporary dumps, keep class indexes updated, and ensure the next engineer finds a codebase they are proud to work in.
