# LeTrain - Developer Documentation & Architecture Guide 🚂

Welcome to the internal engineering documentation for **LeTrain**. This directory contains technical guides, subsystem deep dives, and the historical log of Architectural Decision Records (ADRs).

> 🌐 **Language Note:**  
> The internal developer wiki and ADRs in this directory are written in **Spanish**, the primary working language of LeTrain's author and core development team. Machine translation (DeepL, Google Translate, or browser translation) works seamlessly with these Markdown files.  
> 
> For public-facing, bilingual documentation, see the [User Manual](../user/manual.md) ([Español](../user/manual_es.md)) and the [Design Philosophy & Manifesto](../PHILOSOPHY.md) ([Español](../PHILOSOPHY_es.md)).

---

## 🏛️ High-Level Architecture

LeTrain is engineered as a clean, decoupled simulation platform combining classic ASCII mechanics with modern 3D graphics.

```
                  ┌─────────────────────────────────────┐
                  │          Game Model (core)          │
                  │  World, Trains, Economy, Topology   │
                  └──────────────────┬──────────────────┘
                                     │
                 ┌───────────────────┴───────────────────┐
                 ▼                                       ▼
    ┌─────────────────────────┐             ┌─────────────────────────┐
    │    Terminal Presenter   │             │    Graphic Presenter    │
    │      (ui-terminal)      │             │       (ui-graphic)      │
    └────────────┬────────────┘             └────────────┬────────────┘
                 ▼                                       ▼
    ┌─────────────────────────┐             ┌─────────────────────────┐
    │  Lanterna View (ASCII)  │             │   LibGDX 3D View (GL)   │
    └─────────────────────────┘             └─────────────────────────┘
```

### Core Design Principles
* **Model-View-Presenter (MVP):** Complete separation of game state and user interaction. The `Model` is completely agnostic of views and graphics libraries.
* **Visitor Pattern Rendering:** Entities implement `Renderable` and accept a `Visitor`. `RenderVisitor` renders characters in Lanterna (2D), while `Gdx3DRenderer` renders 3D physical blocks in LibGDX.
* **Functional Symmetry:** The 2D terminal client and the 3D client share 100% of functional capabilities. Every action possible in one is identically possible in the other.
* **Event-Driven Reactivity:** Avoid polling in simulation loops. Segment reservation occurs when the train head enters a node/switch; block release occurs when the tail exits.
* **Custom DSL (ANTLR4):** Players can script routes, signals, and automation via a dedicated programming language running in the console and scenario files.
* **Deterministic Scenarios (`.ltr`):** The same seed + plain-text recipe reconstructs the same world anywhere. Validated headlessly via `letrain-check`.

---

## 🧭 Navigating the Wiki

The technical documentation is organized as an interconnected Obsidian wiki. Start exploring from the main index:

👉 **[Índice Principal de la Wiki (Index.md)](Index.md)**

### Subsystem Reference

* **[Architecture & Design (`architecture/`)](architecture/Overview.md):**
  * System overview, game loop timing, class index, and presenter symmetry.
* **[Railway Infrastructure (`infrastructure/`)](infrastructure/TrackTypes.md):**
  * Track hierarchy (`RailTrack`, `ForkRailTrack`, `BridgeRailTrack`, `TunnelRailTrack`), block system (`BlockManager`), and safety locks.
* **[Rolling Stock & Physics (`vehicles/`)](vehicles/Physics.md):**
  * Train physics, linker mechanics, speed curves, derailments, and cargo logistics.
* **[Event System (`events/`)](events/TrainEvents.md):**
  * Event dispatcher, sensor triggers, semaphore callbacks, and decoupling.
* **[Navigation & Routing (`navigation/`)](navigation/AStarPathfinder.md):**
  * Topological routing, A* pathfinder, and autonomous train autopilot.
* **[Automation & Scripting (`systems/`)](systems/CommandPattern.md):**
  * ANTLR4 command executor, action catalogue, and editor implementation.
* **[Architectural Decision Records (`adr/`)](adr/ADR-000-Design-decisions.md):**
  * Chronological records of all foundational decisions (from ADR-000 to ADR-020).

---

## 🛠️ Build & Development Workflow

```bash
# Build standalone packages for all modules (3D, 2D, letrain-check)
mvn clean package -DskipTests

# Run the test suite
mvn test

# Run a specific unit test
mvn test -Dtest=RailTrackMakerTest
```

For guidelines on coding style, Git branching, and Pull Requests, see **[CONTRIBUTING.md](../../CONTRIBUTING.md)** and **[PHILOSOPHY.md](../PHILOSOPHY.md)**.
