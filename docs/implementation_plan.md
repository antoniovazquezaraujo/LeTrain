# Implementation Plan - Phase B Part 2: Pathfinder & Test Cleanup

This plan describes the proposed changes to align the segment pathfinder (`AStarPathfinder`) with the design specified in ADR-008, and clean up the `AutoPilot` test suite by removing the redundant `AutoPilotStub` and making `AutoPilotTest` verify the real implementation.

## User Review Required

> [!NOTE]
> All changes are backward-compatible and do not change public APIs.
> We add a new default method `getTrackCount(Segment segment)` to the `RailwayGraph` interface, returning `0` by default. This avoids breaking existing mocks.

---

## Proposed Changes

### Navigation & Graph

#### [MODIFY] [RailwayGraph.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/segments/RailwayGraph.java)
- Add a new default method:
  ```java
  default int getTrackCount(Segment segment) {
      return 0;
  }
  ```

#### [MODIFY] [RailwayGraphImpl.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/segments/impl/RailwayGraphImpl.java)
- Add a tracking map for segment tracks:
  ```java
  private final Map<Segment, Set<letrain.track.rail.RailTrack>> segmentToTracks = new HashMap<>();
  ```
- Update `registerTrack(Segment segment, RailTrack track)` to populate `segmentToTracks`:
  ```java
  public void registerTrack(Segment segment, letrain.track.rail.RailTrack track) {
      trackToSegment.putIfAbsent(track, segment);
      segmentToTracks.computeIfAbsent(segment, k -> new HashSet<>()).add(track);
  }
  ```
- Override `getTrackCount(Segment segment)` to return the size of the set from `segmentToTracks`.

#### [MODIFY] [AStarPathfinder.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/AStarPathfinder.java)
- Implement `segmentCost(Segment s)` to use the actual physical track count:
  ```java
  private int segmentCost(Segment s) {
      int count = graph.getTrackCount(s);
      return count > 0 ? count : 1;
  }
  ```
- Restore the `entryDir` check. In the neighbor iteration, if `neighbor.equals(to)` and `entryDir.isPresent()`, find the `PathStep` representing the transition from the current segment to the neighbor segment. If the direction of the transition step (`next.getDir()`) does not match `entryDir.get()`, discard the neighbor.

### Autopilot & Tests

#### [MODIFY] [AutoPilotTest.java](file:///home/antonio/dev/LeTrain/src/test/java/letrain/itinerary/AutoPilotTest.java)
- Remove import and instantiation of `AutoPilotStub`.
- Mock `AutoPilotContext` using Mockito.
- Instantiate `AutoPilotImpl` and verify the test assertions directly against the real implementation.

#### [DELETE] [AutoPilotStub.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/impl/AutoPilotStub.java)
- Delete the redundant stub class.

---

## Verification Plan

### Automated Tests
- Run `mvn clean test` to verify all 326 tests pass successfully.
- Write new unit tests in [SegmentPathfinderTest.java](file:///home/antonio/dev/LeTrain/src/test/java/letrain/itinerary/SegmentPathfinderTest.java) to verify:
  1. That the pathfinder correctly calculates costs using track counts.
  2. That the `entryDir` constraint successfully filters out paths entering from incorrect directions.
