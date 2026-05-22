# Task — Pathfinder & Test Cleanup (Phase B Part 2)

Align AStarPathfinder with ADR-008 segment costs and entryDir constraints, and clean up the AutoPilot test suite.

- [x] Implement physical track count tracking in `RailwayGraph` / `RailwayGraphImpl`
- [x] Refactor `AStarPathfinder.java` to use segment track count for edge costs
- [x] Restore and implement `entryDir` constraint check in `AStarPathfinder.java`
- [x] Refactor `AutoPilotTest.java` to verify `AutoPilotImpl` directly and delete `AutoPilotStub.java`
- [x] Write new unit tests in `SegmentPathfinderTest.java` verifying pathfinder cost and entryDir logic
- [x] Run `mvn clean test` to ensure all tests pass
