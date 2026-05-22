# Walkthrough - Autopilot & Persistence Refactoring

We have successfully completed the refactoring phases for the Autopilot and Itinerary systems, achieving 100% decoupling of domain logic from serialization framework annotations and ensuring full state persistence.

## Phase B: Action/Query Decoupling
- **Goal**: Make `AutoPilotContext` a read-only interface containing only query methods.
- **Changes**:
  - Moved action methods (`ensureForkRoute`, `notifySegmentOccupied`, `forceSegmentReset`) to a new `TrainActionManager` interface.
  - Migrated implementation from `TrainAutoPilotContext` directly to `Train.java`.
  - Updated `AutoPilotImpl` to delegate actions to the `TrainActionManager`.

## Phase C: Jackson Serialization Decoupling & State Persistence
- **Goal**: Strip all Jackson annotations from `Train.java` and domain classes, and guarantee full save/load persistence of autopilot state.
- **Changes**:
  - **Domain Cleanup**: Removed all Jackson annotations from `Train.java`. Runtime references (e.g. `model`, `blockManager`) are correctly re-linked inside `postLoadInit()`.
  - **Jackson Mix-ins**: Created five mix-in classes in `letrain.mvp.impl` to handle serialization details externally:
    - [TrainMixin.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/mvp/impl/TrainMixin.java): Configures property mappings and aliases (`@JsonAlias({"itinerary", "trip"})`) for backward compatibility, renaming historic itineraries to `"trip"`.
    - [WaypointMixin.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/mvp/impl/WaypointMixin.java): Custom serializer and deserializer handling polymorphic waypoint types.
    - [ItineraryMixin.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/mvp/impl/ItineraryMixin.java): Custom serializer/deserializer restoring itinerary steps and active indices.
    - [AutoPilotMixin.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/mvp/impl/AutoPilotMixin.java): Custom serializer/deserializer preserving wait ticks, mode, and pending actions.
    - [WaypointCommandMixin.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/mvp/impl/WaypointCommandMixin.java): Custom deserializer reconstructing commands from JSON properties.
  - **Mix-in Registration**: Registered the mix-ins in [GameSaveService.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/mvp/impl/GameSaveService.java) and [SerializationTest.java](file:///home/antonio/dev/LeTrain/src/test/java/letrain/SerializationTest.java).
  - **Jackson Parsing Fix**: Advanced `JsonParser` instances returned by `JsonNode.traverse()` via `parser.nextToken()` to ensure proper token state before passing to `DeserializationContext.readValue()`.

## Refinement: Wait command seconds conversion
- **Goal**: Standardize the `WAIT` command parameter and representations to always use seconds instead of ticks, aligning with the DSL/user interface.
- **Changes**:
  - Replaced the field `ticks` in [WaypointCommand.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/WaypointCommand.java) with `seconds`, updated its factory from `waitTicks` to `waitSeconds`, and changed its `toString()` and JSON serialization/deserialization to work entirely with seconds.
  - Modified [AutoPilotImpl.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/impl/AutoPilotImpl.java) to convert configured seconds to simulation ticks upon loading a wait command.
  - Updated all command generation and verification tests in [CommandManager.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/command/CommandManager.java), [WaypointCommandTest.java](file:///home/antonio/dev/LeTrain/src/test/java/letrain/itinerary/WaypointCommandTest.java), [WaypointTest.java](file:///home/antonio/dev/LeTrain/src/test/java/letrain/itinerary/WaypointTest.java), [AutoPilotImplTest.java](file:///home/antonio/dev/LeTrain/src/test/java/letrain/itinerary/AutoPilotImplTest.java), and [SerializationTest.java](file:///home/antonio/dev/LeTrain/src/test/java/letrain/SerializationTest.java) to use and verify seconds.
  - Updated ADR documentation ([ADR-008](file:///home/antonio/dev/LeTrain/docs/adr/ADR-008-Itinerary-Redesigned.md) and [ADR-010](file:///home/antonio/dev/LeTrain/docs/adr/ADR-010-Test-Plan-AutoPilot.md)) to consistently define wait periods in seconds.

---

## Verification Results

### Automated Tests
Ran the full test suite via `mvn clean test` successfully:
```
[INFO] Results:
[INFO]
[INFO] Tests run: 329, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

All 329 unit and integration tests compile and pass successfully, confirming that:
1. Autopilot and itinerary state is serialized and deserialized with complete fidelity.
2. Circular references and transient properties are correctly re-linked post-load.
3. Decoupling is 100% complete with no Jackson annotations left in the domain class `Train.java`.
