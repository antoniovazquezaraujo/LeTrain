# Walkthrough: Fix Autopilot Speed Control Issues

This document describes the changes implemented to solve the issue where the train's target speed briefly takes the value of a waypoint action (such as `SPEED 5`) but immediately drops back to 0 (while the engine remains running).

## Problem

When the train executed a waypoint action (e.g. `SPEED 5` or `SPEED 3`), it set the new target speed, advanced the itinerary, and cleared the current route (`currentRoute = List.of()`). 

Because route recalculation did not happen in the same tick after waypoint execution, the `currentRoute` was left empty for the remainder of the tick. 
When `TrainSafetyManager.checkSafety()` ran later in the same tick:
1. It saw that the autopilot route was empty.
2. It failed to find a valid planned next segment and fell back to topological search.
3. This caused block lock checks to fail or lock the wrong path, resulting in `permissionToMove` becoming `false`.
4. As a result, the safety manager immediately overrode the locomotive target speed back to `0` and set a safety retry timer of 300 ticks (15 seconds), stalling the train.

## Changes Implemented

### 1. Autopilot Route Recalculation

#### [MODIFY] [AutoPilotImpl.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/impl/AutoPilotImpl.java)
- In `executeWaypoint()`, the `WAITING` completion block, and the `REVERSING` completion block: immediately call `calculateRoute()` after clearing the route and advancing the itinerary.
- This ensures `currentRoute` is populated before `TrainSafetyManager` runs, allowing it to properly lock the next segment and keep `permissionToMove = true`.

### 2. Locomotive Engine Startup

#### [MODIFY] [Locomotive.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/Locomotive.java)
- Automatically set `engineOn = true` in `setTargetSpeed(int speed)` if `speed > 0`.
- Ensures any speed command (from player, autopilot, console, or scripting) automatically spins up the engine to allow physical movement.

### 3. Tests Added

#### [NEW] [LocomotiveTest.java](file:///home/antonio/dev/LeTrain/src/test/java/letrain/vehicle/impl/rail/LocomotiveTest.java)
- Verifies that target speed > 0 automatically starts the locomotive engine, while speed <= 0 does not.

#### [MODIFY] [AutoPilotImplTest.java](file:///home/antonio/dev/LeTrain/src/test/java/letrain/itinerary/AutoPilotImplTest.java)
- Added `calculatesRouteAfterWaypoint()` test to verify that the route is immediately recalculated in the same tick after a waypoint execution.

## Verification

We ran `mvn clean test` to build the code and execute all tests:
- ✅ New unit tests for automatic engine startup and immediate route recalculation pass successfully.
- ✅ Entire test suite (**320 tests**) passes with **0 failures**.
