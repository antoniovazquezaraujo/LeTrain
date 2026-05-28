# Implementation Plan — Phase D: Dynamic Rerouting at Forks on Blocked Segments

## Goal Description
When a train approaches a junction (Fork) and its next segment is occupied by another train, instead of coming to a full stop and waiting, the train should dynamically check if there is an alternative branch from the Fork that is free.
*   **In Auto Mode (Autopilot)**: If the planned segment is blocked, calculate an alternative A* route starting from the alternative branch. If a valid route to the destination is found and the branch is free, flip the switch and steer the train along the new path without stopping.
*   **In Manual Mode**: If the driver approaches a blocked segment at a Fork, automatically flip the switch to the free branch if available so the driver bypasses the occupied track.

---

## User Review Required

> [!IMPORTANT]
> **Priority of Route vs Cooldown**: If the alternative A* path is significantly longer, the autopilot will still prefer it over waiting, as long as it is free.
> **Manual Switching**: Manual trains bypass safety blocks (they don't stop). However, automatically flipping the switch for them when the current direction is blocked improves game fluidity.

---

## Proposed Changes

### Autopilot Rerouting

#### [MODIFY] [AutoPilotImpl.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/impl/AutoPilotImpl.java)
*   Modify `tick()` during the segment transition checks:
    *   If the next segment in the planned route (`nextSeg`) is occupied (`!ctx.isSegmentFree(nextSeg)`):
        *   Check if the boundary node between the current segment and `nextSeg` is a `ForkRailTrack`.
        *   If it is a Fork, retrieve its alternative branch segments.
        *   For each alternative segment (`altSeg`):
            *   Verify if `ctx.isSegmentFree(altSeg)` is `true`.
            *   If free, run `pathfinder.find(altSeg, targetSeg, entryDir)` to check if a valid path exists to the current waypoint.
            *   If a path is found, switch the active route to this new path, align the fork to `altSeg` using `actionManager.ensureForkRoute(currentSeg, altSeg)`, and proceed.
            *   If no alternative path is free or route recalculation fails, fall back to the original route and let the safety manager stop the train.

---

### Manual Mode Branch Switching

#### [MODIFY] [TrainSafetyManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/rail/impl/TrainSafetyManager.java)
*   In `acquireInitialLocks` and `onSegmentEntered`, if `train.isAutoMode()` is `false` (manual train):
    *   If `nextSegment` is occupied (i.e., `bm.tryLock` fails):
        *   Check if the exit track is a `ForkRailTrack`.
        *   If it is, check if the other alternative branch segment is free in the `BlockManager`.
        *   If the alternative branch is free:
            *   Flip the route of the Fork (`fork.flipRoute()`).
            *   Recalculate `nextSegment` (which will now point to the newly selected branch).
            *   Attempt to lock the new `nextSegment` in the `BlockManager`.

---

## Verification Plan

### Automated Tests
*   Create a new integration test class `ReroutingIntegrationTest.java` in `src/test/java/letrain/itinerary/`:
    *   Setup a Fork connecting to two parallel segments (`SegA` and `SegB`) that merge back later.
    *   Place a dummy train blocking `SegA`.
    *   Configure an autopilot train heading towards the Fork with a route initially planned through `SegA`.
    *   Assert that upon approaching the Fork:
        *   The autopilot detects the blockage on `SegA`.
        *   The autopilot switches the Fork to `SegB`.
        *   The autopilot recalculates the route via `SegB`.
        *   The train successfully reaches the destination without stopping.

### Manual Verification
*   Deploy to a test map, create a fork where one branch is blocked by a parked train, and verify both manual and automatic trains choose the free track.
