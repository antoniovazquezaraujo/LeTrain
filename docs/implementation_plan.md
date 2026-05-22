# Implementation Plan - Decouple Actions from AutoPilotContext

This plan details the refactoring to separate read-only queries from action/mutation operations in the Autopilot module. Specifically, we will migrate actions from `AutoPilotContext` (and its implementation `TrainAutoPilotContext`) to `TrainActionManager` (implemented by `Train`), leaving `AutoPilotContext` as a clean, read-only interface.

## User Review Required

> [!NOTE]
> All changes are backward-compatible. `Train` already implements `TrainActionManager`, so adding the action methods there is highly aligned with its existing role.
> The tests will be updated to verify action execution on the `TrainActionManager` mock instead of the context mock.

---

## Proposed Changes

### Navigation & Autopilot Interfaces

#### [MODIFY] [AutoPilotContext.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/AutoPilotContext.java)
- Remove action/mutation method declarations:
  - `void ensureForkRoute(Segment from, Segment to);`
  - `void notifySegmentOccupied(Segment segment);`
  - `void forceSegmentReset();`
- This leaves the context with only read-only/query methods.

#### [MODIFY] [TrainActionManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/TrainActionManager.java)
- Add the action method declarations removed from `AutoPilotContext`:
  - `void ensureForkRoute(letrain.segments.Segment from, letrain.segments.Segment to);`
  - `void notifySegmentOccupied(letrain.segments.Segment segment);`
  - `void forceSegmentReset();`

---

### Implementation Classes

#### [MODIFY] [TrainAutoPilotContext.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/TrainAutoPilotContext.java)
- Remove overridden implementations of the 3 action methods:
  - `ensureForkRoute(Segment from, Segment to)`
  - `notifySegmentOccupied(Segment segment)`
  - `forceSegmentReset()`
- Remove the private helper method `isAlternativeRouteNeeded(ForkRailTrack fork, letrain.map.Dir targetDir)`.

#### [MODIFY] [Train.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/Train.java)
- Import:
  - `letrain.segments.Segment`
  - `letrain.segments.RailwayGraph`
  - `letrain.segments.RailNode`
  - `letrain.track.rail.ForkRailTrack`
- Add `@Override` to the existing methods:
  - `public void forceSegmentReset()`
  - `public void notifySegmentOccupied(letrain.segments.Segment segment)`
- Implement `public void ensureForkRoute(Segment from, Segment to)` containing the logic moved from `TrainAutoPilotContext.java`.
- Implement `private boolean isAlternativeRouteNeeded(ForkRailTrack fork, letrain.map.Dir targetDir)` helper in `Train.java`.

#### [MODIFY] [AutoPilotImpl.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/impl/AutoPilotImpl.java)
- Update code to call actions on `actionManager` (with null checks) instead of `ctx`:
  - In `activate()`:
    ```java
    if (actionManager != null) {
        actionManager.forceSegmentReset();
    }
    ```
  - In `ensureForkRoute(Segment from, Segment to)`:
    ```java
    if (actionManager != null) {
        actionManager.ensureForkRoute(from, to);
    }
    ```
  - In `tick()` when next segment is occupied:
    ```java
    if (actionManager != null) {
        actionManager.notifySegmentOccupied(nextSeg);
    }
    ```

---

### Tests

#### [MODIFY] [AutoPilotImplTest.java](file:///home/antonio/dev/LeTrain/src/test/java/letrain/itinerary/AutoPilotImplTest.java)
- Update `setUp()` to mock `TrainActionManager` and pass it to the constructor of `AutoPilotImpl`.
- Update verifications to assert action calls (`ensureForkRoute`, `notifySegmentOccupied`) on the `actionManager` mock rather than on `ctx`.

---

## Verification Plan

### Automated Tests
- Run `mvn clean test` to compile and verify all unit and integration tests.
