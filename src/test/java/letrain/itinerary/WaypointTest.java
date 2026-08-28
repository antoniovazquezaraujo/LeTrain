package letrain.itinerary;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import letrain.itinerary.impl.WaypointImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Waypoint")
class WaypointTest {

    @Test
    @DisplayName("should create station waypoint with id")
    void stationWaypoint() {
        Waypoint wp = new WaypointImpl(Waypoint.Type.STATION, 3, List.of(WaypointCommand.LOAD));

        assertEquals(Waypoint.Type.STATION, wp.type());
        assertEquals(3, wp.targetId());
        assertEquals(List.of(WaypointCommand.LOAD), wp.commands());
    }

    @Test
    @DisplayName("should create sensor waypoint without commands")
    void sensorWaypoint() {
        Waypoint wp = new WaypointImpl(Waypoint.Type.SENSOR, 7, List.of());

        assertEquals(Waypoint.Type.SENSOR, wp.type());
        assertEquals(7, wp.targetId());
        assertTrue(wp.commands().isEmpty());
    }

    @Test
    @DisplayName("should allow optional entry direction")
    void withEntryDir() {
        Waypoint wp = new WaypointImpl(Waypoint.Type.STATION, 5, letrain.map.Dir.SW, List.of());

        assertEquals(letrain.map.Dir.SW, wp.entryDir().orElse(null));
    }

    @Test
    @DisplayName("should have empty entryDir when not specified")
    void withoutEntryDir() {
        Waypoint wp = new WaypointImpl(Waypoint.Type.STATION, 5, List.of());

        assertTrue(wp.entryDir().isEmpty());
    }

    @Test
    @DisplayName("should allow REVERSE command on sensor waypoint")
    void sensorWithReverse() {
        Waypoint wp = new WaypointImpl(Waypoint.Type.SENSOR, 2, List.of(WaypointCommand.REVERSE));

        assertTrue(wp.commands().contains(WaypointCommand.REVERSE));
    }

    @Test
    @DisplayName("should allow WAIT with seconds")
    void withWaitCommand() {
        Waypoint wp =
                new WaypointImpl(Waypoint.Type.SENSOR, 1, List.of(WaypointCommand.waitSeconds(15)));

        assertEquals(15, wp.commands().get(0).seconds());
    }
}
