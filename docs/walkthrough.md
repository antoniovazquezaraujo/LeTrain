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
*   Reintroduced a path-crawling route estimation method using `letrain.vehicle.rail.RailIterator`.
*   Removed the static and fragile fallback to logical next steps index zero (`nextSteps.get(0)`).
*   The system now virtually crawls forward from the locomotive using the current active route and actual physical switches/junctions. It reads the real-time physical switch states (main or alternative) of desvíos/forks along the path to accurately predict the next segment the train is heading towards.

### 4. Shunting Coexistence on Unlinking & Load
*   Maintained dynamic shunting lock support (`tryShuntingLock`) during initialization and train divisions (such as decoupling wagons).
*   If two stopped trains end up sharing the same block (e.g. immediately after an unlink division), the `BlockManager` registers co-ownership correctly so both trains remain tracked.
*   The `TrainSafetyManager` immediately detects this co-ownership conflict and triggers `forceEmergencyStop()`, putting automatic trains into manual mode so the player can rescue and pull them apart manually.

### 5. Decoupled and Clean Codebase
*   Domain classes such as [Train.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/Train.java) and [TrainSafetyManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/TrainSafetyManager.java) have been streamlined.
*   Obsolete and redundant method calls like `checkSafety` have been removed in favor of `hasPermissionToMove()`.

## Bug Fix: Resolution of AutoPilotIntegrationTest Failures

During the event-driven refactoring integration phase, a copy-paste error was introduced in `TrainMovementManager.java` inside the `moveOneTrack` execution pass. 

### The Problem
*   The calls to `nextTrackOfLinker.enterLinkerFromDir(entryDirOfLinker, linkerToMove)` and `linkerToMove.setRailsSinceStop(...)` were completely duplicated.
*   The first call moved the linker into the next track. The second call checked if the linker could enter the next track again. Since the track was now occupied by that same linker, the second call failed, triggering a rollback.
*   This caused all train movements to fail and trains to remain stuck at station 0, resulting in the failure of all integration tests where trains were expected to travel.

### The Fix
*   Removed the duplicate code block from [TrainMovementManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/TrainMovementManager.java).
*   Verified that all 331 tests (including the 14 `AutoPilotIntegrationTest` scenarios) now build and pass successfully in green.

## Bug Fix: PulseAudio Mutex Teardown Crash & NaN Audio Failure

### The Problem
*   **NaN Audio Cutouts**: Division by zero in `crossfadeLen` calculation in [GrainEngine.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/audio/synth/GrainEngine.java) generated `NaN` sample values. Since the low-pass filter (LPF) accumulates historical values (`lastVal`), a single `NaN` locked the filter state to `NaN` permanently, cutting out the sound after a few seconds of play.
*   **PulseAudio Crash on Shutdown**: Calling `line.drain()` inside `AudioMixer.java` blocked the audio thread synchronously while holding native PulseAudio locks. If PulseAudio experienced lag or suspended playback, this call blocked indefinitely. When the JVM terminated, the native shutdown thread attempted to free the PulseAudio context and destroy mutexes, resulting in a `pthread_mutex_destroy` assertion crash because the audio thread was still hung holding the mutex.

### The Fix
*   Added `crossfadeLen > 0` validation and sanitized raw and smoothed sample values against `NaN` or `Infinity` in [GrainEngine.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/audio/synth/GrainEngine.java).
*   Replaced the blocking `line.drain()` call with non-blocking `line.stop()` and `line.flush()` calls inside [AudioMixer.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/audio/core/AudioMixer.java).
*   Increased the audio thread `join` timeout to `2000` ms on shutdown to ensure the native mixer thread finishes and closes its resources before the JVM destroys PulseAudio contexts.
