# Goal Description

This implementation plan details the final phase (Phase C) of our Autopilot refactoring.
The main goals are:
1. **Save/Load Persistence**: Persist the autopilot state (including active itineraries, waypoint progress, wait ticks, and pending commands) across game saves/loads.
2. **Decouple Serialization from Domain Classes**: Clean up `Train.java` by moving all Jackson annotations to a separate `TrainMixin.java` file.
3. **JSON Property Naming Clean-up**: Rename the JSON property for the historical log from `"itinerary"` to `"trip"` in `Train`, adding alias support (`@JsonAlias`) for backward-compatibility with older save files.
4. **Clean up Redundant Casts and Formatting**: Fix minor layout issues and unnecessary casts.

## User Review Required

> [!NOTE]
> **Backward Compatibility**: Older save files containing `"itinerary"` for the historic station log will still load successfully because we use `@JsonAlias({"itinerary", "trip"})` on the mix-in.
> **AutoPilot Deserialization**: The pathfinder and context links are transient and circular. They will be automatically restored during the `postLoadInit()` lifecycle method.

---

## Proposed Changes

### Core Serialization / Mix-ins

#### [NEW] [TrainMixin.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/mvp/impl/TrainMixin.java)
- Mirror `Train.java` structure and define all JSON serialization rules:
  - Add `@JsonIdentityInfo` and `@JsonIgnoreProperties`.
  - Add `@JsonProperty("linkers")` and `@JsonDeserialize(as = LinkedList.class)`.
  - Add `@JsonUnwrapped` for `logisticsManager`.
  - Configure `@JsonProperty("trip")` and `@JsonAlias({"itinerary", "trip"})`.
  - Remove `@JsonIgnore` from `autopilot` and `autoMode` to persist them.
  - Mark all query/view/getter methods with `@JsonIgnore`.

#### [NEW] [WaypointMixin.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/mvp/impl/WaypointMixin.java)
- Define serializer `WaypointSerializer` and deserializer `WaypointDeserializer` to handle `Waypoint` interface / `WaypointImpl` record custom serialization (including `Optional<Dir>` and command lists without requiring extra jdk8 modules).
- Map `Waypoint` and `WaypointImpl` to use these custom serializers/deserializers.

#### [NEW] [ItineraryMixin.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/mvp/impl/ItineraryMixin.java)
- Define custom serializer `ItinerarySerializer` and deserializer `ItineraryDeserializer` for the `Itinerary` interface / `ItineraryImpl`.
- Reconstruct the `ItineraryImpl` using its new package-private constructor.

#### [NEW] [AutoPilotMixin.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/mvp/impl/AutoPilotMixin.java)
- Define custom serializer `AutoPilotSerializer` and deserializer `AutoPilotDeserializer` for `AutoPilot` / `AutoPilotImpl` to capture waypoints, wait ticks, mode, and pending commands.

#### [NEW] [WaypointCommandMixin.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/mvp/impl/WaypointCommandMixin.java)
- Map JSON properties to `WaypointCommand` using a custom static `@JsonCreator` method, bypassing private constructors.

---

### Decoupling & Adjusting Domain Classes

#### [MODIFY] [Train.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/Train.java)
- Remove all Jackson annotations (imports and field/method decorations).
- In `postLoadInit()`:
  - If `autopilot` is not null, invoke `reinitialize(new TrainAutoPilotContext(this), this)` and set up its A* pathfinder.
- Refactor redundant code (remove the cast to `(Tractor)` at line 467, simplify `getTractors()` steam to list).

#### [MODIFY] [AutoPilotImpl.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/impl/AutoPilotImpl.java)
- Remove `final` keyword from `ctx` and `actionManager` to allow them to be reinitialized after deserialization.
- Add getter/setter for `waitTicks` and `pendingCommands` to facilitate serialization.
- Add constructor `public AutoPilotImpl()` initializing `ctx` and `actionManager` to `null`.
- Add constructor `public AutoPilotImpl(Itinerary itinerary, Mode mode, int waitTicks, List<WaypointCommand> pendingCommands)` for deserializer.
- Add `public void reinitialize(AutoPilotContext ctx, TrainActionManager actionManager)`.

#### [MODIFY] [ItineraryImpl.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/itinerary/impl/ItineraryImpl.java)
- Add package-private constructor `ItineraryImpl(List<Waypoint> waypoints, Set<Integer> assignedTrains, State state, int currentIndex)` for deserialization.

---

### Game Save & Tests Configuration

#### [MODIFY] [GameSaveService.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/mvp/impl/GameSaveService.java)
- Register the new Mix-ins:
  - `mapper.addMixIn(Train.class, TrainMixin.class);`
  - `mapper.addMixIn(Waypoint.class, WaypointMixin.class);`
  - `mapper.addMixIn(WaypointImpl.class, WaypointMixin.class);`
  - `mapper.addMixIn(Itinerary.class, ItineraryMixin.class);`
  - `mapper.addMixIn(ItineraryImpl.class, ItineraryMixin.class);`
  - `mapper.addMixIn(AutoPilot.class, AutoPilotMixin.class);`
  - `mapper.addMixIn(AutoPilotImpl.class, AutoPilotMixin.class);`
  - `mapper.addMixIn(WaypointCommand.class, WaypointCommandMixin.class);`

#### [MODIFY] [SerializationTest.java](file:///home/antonio/dev/LeTrain/src/test/java/letrain/SerializationTest.java)
- Update `serialize` and `deserialize` helper methods to register all the same Mix-ins on their `ObjectMapper`.
- Add a new integration test `testTrainAutoPilotSerialization()` that:
  - Sets up an itinerary with waypoints and commands.
  - Instantiates `AutoPilotImpl` and assigns it to a `Train`.
  - Serializes and deserializes the `Model`/`Train`.
  - Asserts that the autopilot state (mode, wait ticks, itinerary, waypoints, commands) is successfully preserved and reinitialized.

---

## Verification Plan

### Automated Tests
- Run `mvn clean test` to compile and verify all unit/integration tests (including the new serialization integration tests).
