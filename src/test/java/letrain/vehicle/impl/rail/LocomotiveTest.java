package letrain.vehicle.impl.rail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Locomotive Engine and Speed Logic Tests")
class LocomotiveTest {

    @Test
    @DisplayName("should turn on engine when target speed is set to positive value")
    void shouldTurnOnEngineWhenTargetSpeedIsPositive() {
        Locomotive locomotive = new Locomotive(1, "A");
        assertFalse(locomotive.isEngineOn(), "engine should be off initially");

        locomotive.setTargetSpeed(5);
        assertTrue(locomotive.isEngineOn(), "engine should turn on automatically when setting speed > 0");
        assertEquals(5, locomotive.getTargetSpeed(), "target speed should be updated correctly");
    }

    @Test
    @DisplayName("should not turn on engine when target speed is set to zero or negative value")
    void shouldNotTurnOnEngineWhenTargetSpeedIsZeroOrNegative() {
        Locomotive locomotive = new Locomotive(2, "B");
        assertFalse(locomotive.isEngineOn(), "engine should be off initially");

        locomotive.setTargetSpeed(0);
        assertFalse(locomotive.isEngineOn(), "engine should remain off when speed is 0");

        locomotive.setTargetSpeed(-1);
        assertFalse(locomotive.isEngineOn(), "engine should remain off when speed is negative");
    }
}
