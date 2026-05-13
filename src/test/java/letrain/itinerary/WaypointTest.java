package letrain.itinerary;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

@DisplayName("Waypoint")
class WaypointTest {

    @Test
    @DisplayName("should create station waypoint with id")
    void stationWaypoint() {
        Waypoint wp = new Waypoint(Waypoint.Type.STATION, 3, List.of(WaypointCommand.LOAD));

        assertEquals(Waypoint.Type.STATION, wp.type());
        assertEquals(3, wp.targetId());
        assertEquals(List.of(WaypointCommand.LOAD), wp.commands());
    }

    @Test
    @DisplayName("should create sensor waypoint without commands")
    void sensorWaypoint() {
        Waypoint wp = new Waypoint(Waypoint.Type.SENSOR, 7, List.of());

        assertEquals(Waypoint.Type.SENSOR, wp.type());
        assertEquals(7, wp.targetId());
        assertTrue(wp.commands().isEmpty());
    }

    @Test
    @DisplayName("should allow optional entry direction")
    void withEntryDir() {
        Waypoint wp = new Waypoint(Waypoint.Type.STATION, 5, letrain.map.Dir.SW, List.of());

        assertEquals(letrain.map.Dir.SW, wp.entryDir().orElse(null));
    }

    @Test
    @DisplayName("should have empty entryDir when not specified")
    void withoutEntryDir() {
        Waypoint wp = new Waypoint(Waypoint.Type.STATION, 5, List.of());

        assertTrue(wp.entryDir().isEmpty());
    }

    @Test
    @DisplayName("should allow REVERSE command on sensor waypoint")
    void sensorWithReverse() {
        Waypoint wp = new Waypoint(Waypoint.Type.SENSOR, 2, List.of(WaypointCommand.REVERSE));

        assertTrue(wp.commands().contains(WaypointCommand.REVERSE));
    }

    @Test
    @DisplayName("should allow WAIT with ticks")
    void withWaitCommand() {
        Waypoint wp = new Waypoint(Waypoint.Type.SENSOR, 1, List.of(WaypointCommand.waitTicks(300)));

        assertEquals(300, wp.commands().get(0).ticks());
    }
}
