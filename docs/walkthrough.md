# Phase B/C Refactoring — Purely Event-Driven Train Safety & Cantons System

We have successfully completed the refactoring of the LeTrain train safety system. The project compiles successfully and all integration and unit tests pass in green.

## Key Accomplishments

### 1. Purely Event-Driven Reactivity
*   Removed all legacy tick-based safety polling and manual retry timers (such as the legacy `safetyRetryTimer = 250`).
*   Replaced the polling model with a reactive wakeup event system:
    *   Trains check their safety blocks strictly at discrete lifecycle events: **start of movement** (`acquireInitialLocks`), **segment boundary crossings** (`onSegmentEntered`), and **direction inversion** (`onReverse`).
    *   When blocked by an occupied segment, automatic trains brake to `0` and set `isWaitingForBlock = true`.
    *   When any segment is released, the `BlockManager` fires a broadcast to the `Model` which wakes up all waiting trains (`safetyManager.wakeUp(model)`). The waiting trains reactively retry the lock and resume movement seamlessly.

### 2. Precise Direction of Travel (`getRealDir()`)
*   Resolved a critical direction bug when trains move in reverse or have reversed locomotives (`isReversed() == true`).
*   Instead of blindly checking `head.getDir()`, the system now uses `head.getRealDir()`, which is a polymorphic method in [Tracker.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/Tracker.java#L24-L30) that dynamically retrieves the correct physical exit direction based on the locomotive's orientation and track connectivity.

### 3. Dynamic Node & Canton Detection (`RailIterator`)
*   Reintroduced a path-crawling route estimation method using `letrain.vehicle.impl.RailIterator`.
*   Removed the static and fragile fallback to logical next steps index zero (`nextSteps.get(0)`).
*   The system now virtually crawls forward from the locomotive using the current active route and actual physical switches/junctions. It reads the real-time physical switch states (main or alternative) of desvíos/forks along the path to accurately predict the next segment the train is heading towards.

### 4. Shunting Coexistence on Unlinking & Load
*   Maintained dynamic shunting lock support (`tryShuntingLock`) during initialization and train divisions (such as decoupling wagons).
*   If two stopped trains end up sharing the same block (e.g. immediately after an unlink division), the `BlockManager` registers co-ownership correctly so both trains remain tracked.
*   The `TrainSafetyManager` immediately detects this co-ownership conflict and triggers `forceEmergencyStop()`, putting automatic trains into manual mode so the player can rescue and pull them apart manually.

### 5. Decoupled and Clean Codebase
*   Domain classes such as [Train.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/Train.java) and [TrainSafetyManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/TrainSafetyManager.java) have been streamlined.
*   Obsolete and redundant method calls like `checkSafety` have been removed in favor of `hasPermissionToMove()`.
