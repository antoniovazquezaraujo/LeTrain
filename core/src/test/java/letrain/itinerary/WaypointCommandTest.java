package letrain.itinerary;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WaypointCommand")
class WaypointCommandTest {

    @Test
    @DisplayName("should have load and unload commands")
    void loadUnload() {
        assertEquals(WaypointCommand.Kind.LOAD, WaypointCommand.LOAD.kind());
        assertEquals(WaypointCommand.Kind.UNLOAD, WaypointCommand.UNLOAD.kind());
    }

    @Test
    @DisplayName("should have reverse command with no seconds")
    void reverseHasNoSeconds() {
        assertEquals(0, WaypointCommand.REVERSE.seconds());
    }

    @Test
    @DisplayName("WAIT should store seconds")
    void waitStoresSeconds() {
        WaypointCommand wait = WaypointCommand.waitSeconds(15);
        assertEquals(15, wait.seconds());
    }

    @Test
    @DisplayName("SPEED should store target speed")
    void speedStoresTarget() {
        WaypointCommand speed = WaypointCommand.speed(5);
        assertEquals(5, speed.targetSpeed());
    }

    @Test
    @DisplayName("NONE should have no parameters")
    void noneHasNoParams() {
        assertEquals(0, WaypointCommand.NONE.seconds());
        assertEquals(0, WaypointCommand.NONE.targetSpeed());
    }

    @Test
    @DisplayName("should identify REVERSE command")
    void isReverse() {
        assertTrue(WaypointCommand.REVERSE.isReverse());
        assertFalse(WaypointCommand.LOAD.isReverse());
    }
}
