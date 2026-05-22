# Task — Waypoint Command Execution

Implement and verify the execution of itinerary waypoint commands (LOAD, UNLOAD, REVERSE, SPEED, and WAIT) in the autopilot.

- [x] Define `TrainActionManager` interface to decouple AutoPilot from Train internals
- [x] Modify `Train` to implement `TrainActionManager` and execute commands on the physical locomotive and wagons
- [x] Update `CommandManager` to pass `Train` to the `AutoPilotImpl` constructor
- [x] Implement execution queue (`pendingCommands`) and `WAITING` state machine in `AutoPilotImpl`
- [x] Add unit tests in `AutoPilotImplTest` for command execution and wait handling
- [x] Fix the wait tick transition logic in `AutoPilotImpl.java` and align with test assertions
- [x] Run `mvn clean test` to ensure all 326 tests pass successfully
