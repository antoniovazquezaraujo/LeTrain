# Implementation Plan - Rename vehicle-specific Itinerary to Trip

To resolve the class name conflict between the legacy `letrain.vehicle.impl.rail.Itinerary` (which records train station stop history) and the new DSL-based autopilot interface `letrain.itinerary.Itinerary`, we will rename the legacy class to `Trip`.

## Proposed Changes

### [Component: Vehicle/Rail]

#### [NEW] [Trip.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/Trip.java)
- Create `Trip.java` which is a rename of `letrain.vehicle.impl.rail.Itinerary`.
- Rename class `Itinerary` to `Trip`.
- Rename enum `ItineraryState` to `TripState`.
- Annotate `stops` and `state` fields with Jackson `@JsonProperty` annotations to ensure they serialize properly.

#### [DELETE] [Itinerary.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/Itinerary.java)
- Remove the legacy `letrain.vehicle.impl.rail.Itinerary.java` file.

#### [MODIFY] [Train.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/vehicle/impl/rail/Train.java)
- Rename field `Itinerary itinerary;` to `Trip trip;`.
- Annotate the field with `@com.fasterxml.jackson.annotation.JsonProperty("itinerary")` to maintain JSON compatibility (saving/loading files).
- Rename `public Itinerary getItinerary()` to `public Trip getTrip()`.
- Update `recordStopAtStation()` to use `Trip` instead of `Itinerary`.

### [Component: Services]

#### [MODIFY] [SimulationService.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/mvp/impl/services/SimulationService.java)
- Update calls from `train.getItinerary()` to `train.getTrip()`.

### [Component: View/Presenter]

#### [MODIFY] [TerminalPresenter.java](file:///home/antonio/dev/LeTrain/src/main/java/letrain/mvp/impl/terminal/TerminalPresenter.java)
- Update commented-out legacy code that referenced `train.getItinerary()` to use `train.getTrip()`.

### [Component: Tests]

#### [NEW] [TripTest.java](file:///home/antonio/dev/LeTrain/src/test/java/letrain/vehicle/impl/rail/TripTest.java)
- Create unit tests for `Trip` (similar to what was tested/implied, checking stop additions and state transitions).

## Verification Plan

### Automated Tests
- Run `mvn clean test` to ensure that all 318+ tests compile and pass.
- Run `mvn clean test -Dtest=TripTest` to verify the new unit tests.
