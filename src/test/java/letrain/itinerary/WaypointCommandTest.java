package letrain.itinerary;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WaypointCommand")
class WaypointCommandTest {

    @Test
    @DisplayName("should have load and unload commands")
    void loadUnload() {
        assertEquals(WaypointCommand.Kind.LOAD, WaypointCommand.LOAD.kind());
        assertEquals(WaypointCommand.Kind.UNLOAD, WaypointCommand.UNLOAD.kind());
    }

    @Test
    @DisplayName("should have reverse command with no ticks")
    void reverseHasNoTicks() {
        assertEquals(0, WaypointCommand.REVERSE.ticks());
    }

    @Test
    @DisplayName("WAIT should store ticks")
    void waitStoresTicks() {
        WaypointCommand wait = WaypointCommand.waitTicks(300);
        assertEquals(300, wait.ticks());
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
        assertEquals(0, WaypointCommand.NONE.ticks());
        assertEquals(0, WaypointCommand.NONE.targetSpeed());
    }

    @Test
    @DisplayName("should identify REVERSE command")
    void isReverse() {
        assertTrue(WaypointCommand.REVERSE.isReverse());
        assertFalse(WaypointCommand.LOAD.isReverse());
    }
}
