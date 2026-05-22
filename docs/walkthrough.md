# Walkthrough - AutoPilot Simplification

We have simplified the `AutoPilot` system to satisfy the requirements in ADR-012, removing complex logic such as speed control, wait times, reversing, and loading/unloading, and focusing it solely on segment fork routing and occupied segment event dispatching.

## Changes Made

### Core Logic
1. **`AutoPilotImpl.java`**: Simplified to remove all speed-limit adjustment, waiting timers, automatic reversing, and waypoint action executions. It now tracks the itinerary, calculates paths between waypoints, flips segment forks on segment entrance, and dispatches occupied segment warnings if target segments are not clear.
2. **`TrainAutoPilotContext.java`**: Removed the segment fallback check from `isAtTarget`, ensuring waypoint stations are only flagged as reached when a train linker is physically on the station track.
3. **`TrainEventListener.java` & `Train.java`**: Added the `onSegmentOccupied` event mechanism to broadcast occupied warnings.

### Verification and Tests
1. **`AutoPilotIntegrationTest.java`**:
   - Adjusted `CircuitFromSave.madridToBarcelona` to run for `1200` ticks instead of `500` to allow the train to physically reach the Barcelona station track (as the segment-level arrival fallback was removed).
   - Ensured manual speed is set during setup so the autopilot does not manage it.
2. **`AutoPilotImplTest.java`**: Simplified unit tests to match the new autopilot scope (route/itinerary updates and occupied segment checking, ignoring removed speed/wait controls).

## Validation Results

Running the full Maven clean and test cycle compiles cleanly and passes all 317 tests:

```bash
mvn clean test
```

### Output Summary
```
[INFO] Results:
[INFO]
[INFO] Tests run: 317, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```
All integration and unit tests are fully green.

## Direct Train Commands

We added support for direct train commands (e.g. `train 1 set speed 3;`) in DSL programs so train movement can be controlled by scripts when the simplified AutoPilot is active.

### Changes Made

1. **`LeTrainProgram.g4`**: Added `directTrainCommand` to `directCommand`.
2. **`CommandManager.java`**: Extracted train action parsing logic into `buildTrainAction(...)` and implemented `visitDirectTrainCommand(ctx)`.
3. **`AutoPilotContext` & `TrainAutoPilotContext`**: Removed unused methods (`targetSpeed`, `setTargetSpeed`, `reverse`, `load`, `unload`) to simplify the API and ensure code cleanliness.
4. **`AutoPilotIntegrationTest.java`**:
   - Modified train placement helper to initialize train target speed to 0.
   - Updated integration test programs to start trains using direct DSL commands (e.g., `train %d set speed 3;`), validating grammar and executor integration.

