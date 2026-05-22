# Task — Phase C: Architecture & UX Improvements

Implement itinerary and autopilot save/load persistence using Jackson Mix-ins, clean up JSON naming properties, and perform layout and redundant code refactoring.

- [x] Create git branch `feature/autopilot-persistence`
- [x] Implement domain changes
  - [x] Add reinitialize method and non-final fields to `AutoPilotImpl.java`
  - [x] Add deserialization constructor to `ItineraryImpl.java`
  - [x] Remove Jackson annotations and clean up redundant code/casts in `Train.java`
- [x] Create Jackson Mix-ins
  - [x] Implement `TrainMixin.java` with aliases and ignored getters
  - [x] Implement `WaypointMixin.java` with custom serializer/deserializer
  - [x] Implement `ItineraryMixin.java` with custom serializer/deserializer
  - [x] Implement `AutoPilotMixin.java` with custom serializer/deserializer
  - [x] Implement `WaypointCommandMixin.java` with custom serializer/deserializer
- [x] Register Mix-ins in `GameSaveService.java`
- [x] Update and expand integration tests
  - [x] Register Mix-ins on `ObjectMapper` in `SerializationTest.java`
  - [x] Implement `testTrainAutoPilotSerialization()` in `SerializationTest.java`
- [x] Run `mvn clean test` to verify all tests pass
- [x] Refactor WAIT command to use and represent seconds instead of ticks across domain, serialization, and tests
