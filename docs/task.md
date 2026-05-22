# Tasks - AutoPilot Simplification & Direct Train Commands

## AutoPilot Simplification (Completed)
- [x] Create ADR-012 documenting the AutoPilot simplification.
- [x] Add `onSegmentOccupied` event to `TrainEventListener`.
- [x] Add `notifySegmentOccupied` to `Train`.
- [x] Add `notifySegmentOccupied` to `AutoPilotContext`.
- [x] Implement `notifySegmentOccupied` in `TrainAutoPilotContext`.
- [x] Simplify `AutoPilotImpl` (remove speed control, waypoint actions, tick logic).
- [x] Update `TrainAutoPilotContext` to fix `isAtTarget` segment fallback check.
- [x] Update `AutoPilotImplTest` to match simplified logic.
- [x] Update `AutoPilotIntegrationTest` to match simplified logic and set manual speeds.
- [x] Verify that all tests compile and pass.

## Direct Train Commands (Completed)
- [x] Extend ANTLR grammar `LeTrainProgram.g4` to support `directTrainCommand`.
- [x] Extract train action helper `buildTrainAction` in `CommandManager.java`.
- [x] Implement `visitDirectTrainCommand` in `CommandManager.java`.
- [x] Clean up unused speed, reversing, and loading/unloading interfaces/implementations from `AutoPilotContext` and `TrainAutoPilotContext` to ensure the API is clear, minimal, and focused.
- [x] Update `AutoPilotIntegrationTest.java` to use the new DSL speed setting.
- [x] Run `mvn clean test` and verify compile/test success.
