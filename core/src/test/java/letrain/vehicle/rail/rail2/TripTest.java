package letrain.vehicle.rail.rail2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import letrain.vehicle.rail.impl.Stop;
import letrain.vehicle.rail.impl.Trip;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Trip Unit Tests")
class TripTest {

    @Test
    @DisplayName("should initialize trip with default state")
    void should_InitializeTripWithDefaultState() {
        // Arrange & Act
        letrain.vehicle.rail.Trip trip = new Trip();

        // Assert
        assertEquals(letrain.vehicle.rail.Trip.TripState.CONSTRUCTED, trip.getState());
        assertTrue(trip.getStopsList().isEmpty());
        assertNull(trip.getFirstStop());
    }

    @Test
    @DisplayName("should transition state when stops are added")
    void should_TransitionState_When_StopsAreAdded() {
        // Arrange
        letrain.vehicle.rail.Trip trip = new Trip();
        Stop stop1 = new Stop(1, LocalDateTime.now(), 100);
        Stop stop2 = new Stop(2, LocalDateTime.now(), 200);

        // Act & Assert 1: First stop transitions to STARTING
        trip.addStop(stop1);
        assertEquals(letrain.vehicle.rail.Trip.TripState.STARTING, trip.getState());
        assertEquals(stop1, trip.getFirstStop());

        // Act & Assert 2: Second unique stop transitions to STOPPING
        trip.addStop(stop2);
        assertEquals(letrain.vehicle.rail.Trip.TripState.STOPPING, trip.getState());

        // Act & Assert 3: Repeating a stop transitions to AT_END
        Stop stop1Repeat = new Stop(1, LocalDateTime.now(), 300);
        trip.addStop(stop1Repeat);
        assertEquals(letrain.vehicle.rail.Trip.TripState.AT_END, trip.getState());
    }

    @Test
    @DisplayName("should restart trip stop list")
    void should_RestartTripStopList() {
        // Arrange
        letrain.vehicle.rail.Trip trip = new Trip();
        Stop stop1 = new Stop(1, LocalDateTime.now(), 100);
        Stop stop2 = new Stop(2, LocalDateTime.now(), 200);
        trip.addStop(stop1);
        trip.addStop(stop2);

        // Act
        Stop newStartStop = new Stop(3, LocalDateTime.now(), 300);
        trip.restart(newStartStop);

        // Assert
        List<Stop> stops = trip.getStopsList();
        assertEquals(1, stops.size());
        assertEquals(newStartStop, stops.get(0));
        assertEquals(letrain.vehicle.rail.Trip.TripState.STARTING, trip.getState());
    }

    @Test
    @DisplayName("setStops(null) should be treated as empty list — no NPE")
    void should_TreatNullStopsAsEmpty_When_SetViaSetStops() {
        // Arrange: simulate Jackson deserialization writing null
        letrain.vehicle.rail.Trip trip = new Trip();
        trip.setStops(null);

        // Act & Assert: no NPE when getting or adding
        assertEquals(0, trip.getStopsList().size());
        assertNotNull(trip.getStops());

        Stop stop = new Stop(1, LocalDateTime.now(), 0);
        trip.addStop(stop); // must not throw
        assertEquals(1, trip.getStopsList().size());
    }

    @Test
    @DisplayName("addStop should be safe when internal stops is null")
    void should_HandleNullInternalStops_When_AddStop() {
        // Arrange: use setStops(null) to force stops=null, then bypass the setter
        // by relying on the null-check inside addStop itself
        letrain.vehicle.rail.Trip trip = new Trip();
        trip.setStops(null);

        // Act
        Stop stop = new Stop(5, LocalDateTime.now(), 10);
        trip.addStop(stop);

        // Assert
        assertEquals(letrain.vehicle.rail.Trip.TripState.STARTING, trip.getState());
        assertEquals(1, trip.getStopsList().size());
    }
}
