# Task — Decouple Actions from AutoPilotContext

Move action/mutation methods from `AutoPilotContext` to `TrainActionManager`, implement them in `Train`, update `AutoPilotImpl`, and adapt test cases.

- [x] Create git branch `feature/autopilot-actions-refactor`
- [x] Modify `AutoPilotContext.java` to remove action declarations
- [x] Modify `TrainActionManager.java` to add action declarations
- [x] Modify `TrainAutoPilotContext.java` to remove action implementations
- [x] Modify `Train.java` to implement `ensureForkRoute` and annotate action overrides
- [x] Modify `AutoPilotImpl.java` to execute actions via `actionManager`
- [x] Update `AutoPilotImplTest.java` to mock and verify actions on `TrainActionManager`
- [x] Run `mvn clean test` to verify all tests compile and pass successfully
