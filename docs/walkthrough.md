# Walkthrough - Waypoint Command Execution

We have implemented dynamic execution of itinerary waypoint commands (`LOAD`, `UNLOAD`, `REVERSE`, `SPEED`, and `WAIT`) in the autopilot system.

## Changes Made

### 1. Decoupled Interface: `TrainActionManager`
* **File:** [TrainActionManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/TrainActionManager.java)
* **Design:** Introduced a clean interface that decouples the `AutoPilot` module (which resides in the logical `letrain.itinerary` package) from physical train and carriage details (in `letrain.vehicle`). It defines a single method:
  ```java
  void executeCommand(WaypointCommand command);
  ```

### 2. Physical Execution: `Train.java`
* **File:** [Train.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/Train.java)
* **Logic:** Implemented the `TrainActionManager` interface on `Train`. Added `executeCommand(...)` which:
  - Triggers station loading (`startLoadProcess`) or unloading (`startUnloadProcess`) for `LOAD` and `UNLOAD` commands.
  - Toggles the reverse state of the train's director linker for the `REVERSE` command.
  - Sets the speed on the director linker for the `SPEED` command.

### 3. Autopilot Instantiation: `CommandManager.java`
* **File:** [CommandManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/command/CommandManager.java)
* **Logic:** Updated the autopilot creation call to pass the `Train` instance as the `TrainActionManager` parameter:
  ```java
  train.setAutopilot(new letrain.itinerary.impl.AutoPilotImpl(
      new letrain.vehicle.impl.rail.TrainAutoPilotContext(train),
      train));
  ```

### 4. Queue & WAITING State Machine: `AutoPilotImpl.java`
* **File:** [AutoPilotImpl.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/impl/AutoPilotImpl.java)
* **Logic:**
  - Added a queue `pendingCommands` to store the commands of the current waypoint.
  - On arrival at a waypoint, we populate `pendingCommands` and execute them in sequence.
  - When we hit a `WAIT` command, we pause execution, set `waitTicks`, and transition the autopilot mode to `Mode.WAITING`.
  - On subsequent `tick()` calls while in `Mode.WAITING`, we decrement `waitTicks`. Once `waitTicks` reaches 0, we resume executing the remaining commands in the queue (or transition back to `Mode.FOLLOWING` and advance the itinerary).

### 5. Robust Unit Testing: `AutoPilotImplTest.java`
* **File:** [AutoPilotImplTest.java](file:///home/antonio/dev/LeTrain/src/test/java/letrain/itinerary/AutoPilotImplTest.java)
* **Logic:**
  - Added `should_ExecuteCommands_When_WaypointReached` to verify immediate command sequences.
  - Added `should_HandleWaitCommand_When_WaypointReached` to verify wait state handling, decrement timing, and resume execution.

---

## Validation Results

Running the full Maven clean and test cycle compiles cleanly and passes all 326 tests:

```bash
mvn clean test
```

### Output Summary
```
[INFO] Results:
[INFO]
[INFO] Tests run: 326, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```
All integration and unit tests are fully green.
