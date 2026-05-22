# Implementation Plan - Phase A Bug Fixes & Pull Request

This plan describes the proposed changes to address the critical bugs identified in Phase A of the system analysis, specifically targetting null safety, casting issues, and performance bottlenecks in the Itinerary and AutoPilot systems.

## User Review Required

> [!NOTE]
> All changes are backward-compatible and do not change public APIs.
> A 100-tick (5-second) cooldown is introduced in `AutoPilotImpl` when pathfinding fails, avoiding CPU thrashing while allowing automatic recovery when track layout changes.

---

## Proposed Changes

### Itinerary & DSL Parser

#### [MODIFY] [CommandManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/command/CommandManager.java)
- In `buildTrainAction(...)`, eliminate direct casts to `Locomotive` for speed and direction changes.
- Use the `Tractor` interface directly, since it already defines `setSpeed`, `incSpeed`, `decSpeed`, `isReversed`, and `toggleReversed`.
- Add null checks for `t.getDirectorLinker()` to prevent `NullPointerException`s when a train has no active director linker.

#### [MODIFY] [Trip.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/Trip.java)
- Add null-safety guards in `addStop`, `getFirstStop`, and `getStops` (return empty stream if `stops` is null).
- In `setStops(...)`, ensure that passing `null` defaults to an empty list.
- Prevent potential NPEs after Jackson deserializes a `Trip` with missing/null stops.

#### [MODIFY] [AutoPilotImpl.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/impl/AutoPilotImpl.java)
- Introduce a `routeRetryCooldown` counter (default `0`).
- If `calculateRoute()` fails inside `tick()`, set the cooldown to `100` ticks (approx. 5 seconds at 20fps) and return `false`, instead of retrying every tick.
- Reset the cooldown to `0` in `activate()` and `setItinerary()`.

#### [MODIFY] [Train.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/Train.java)
- Clean up redundant casts to `(Tractor)` on line 467.
- Replace verbose reflection in `getTractors()` with clean `instanceof` check and `Stream.toList()`.
- Add null-safety checks when casting director linkers to `Locomotive`/`Linker`.

---

## Verification Plan

### Automated Tests
- Run `mvn clean test` to ensure all 321 tests pass, including `AutoPilotImplTest` and `AutoPilotIntegrationTest`.
- Create a new unit test in [AutoPilotImplTest.java](file:///home/antonio/dev/LeTrain/src/test/java/letrain/itinerary/AutoPilotImplTest.java) to verify that route calculation failures trigger the cooldown and do not invoke the pathfinder on every tick.
- Create a unit test verifying `Trip` null stops deserialization behavior.

### Manual Verification
- Verify code compilation and checkstyle limits.
