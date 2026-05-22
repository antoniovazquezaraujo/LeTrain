# Implementation Plan: Fix Autopilot Speed Control Issues

This plan describes the proposed modifications to resolve the remaining speed control issues in LeTrain's autopilot.

## User Review Required

> [!NOTE]
> The issue where the target speed drops back to 0 immediately after a waypoint command (like `SPEED 5`) executes is caused by the safety manager.
> When a waypoint is executed, the route is cleared (`currentRoute = List.of()`). In the same tick, `TrainSafetyManager.checkSafety()` runs.
> Because the route is empty, it falls back to a topological search or fails to locate/lock the next segment, resulting in `permissionToMove = false`.
> This forces the train's target speed back to 0.

## Proposed Changes

### Autopilot & Waypoint Navigation

#### [MODIFY] [AutoPilotImpl.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/impl/AutoPilotImpl.java)
- Update `executeWaypoint()`, the `WAITING` completion block in `tick()`, and the `REVERSING` completion block in `tick()` to immediately call `calculateRoute()` after calling `itinerary.advance()` and clearing the route.
- This ensures that `currentRoute` is populated immediately within the same tick. When `TrainSafetyManager.checkSafety()` runs later in the same tick, it can find the correct `nextSegment` from the populated route, acquire block locks, and keep `permissionToMove` set to `true`, preventing target speed from dropping back to 0.

---

### Locomotive Speed Control & Engine Management

#### [MODIFY] [Locomotive.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/Locomotive.java)
- In `setTargetSpeed(int speed)`, if `speed > 0`, set `engineOn = true`.
- This ensures any automation, UI input, or console `SPEED` command automatically turns on the engine so the train can run.

## Verification Plan

## Automated Tests
- Run `mvn clean test` to compile and execute the entire test suite.
- Create an integration test in `AutoPilotIntegrationTest.java` to verify that when a waypoint executes a `SPEED` command, the target speed remains at the commanded speed and does not drop back to 0.
