package letrain.vehicle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Locate flash of the cursor (issue #696): the 'o' key triggers a brief, bright ping. */
@DisplayName("Cursor: locate flash timing")
class CursorTest {

    @Test
    @DisplayName("a ping is active from its instant until the flash duration elapses")
    void isPinging_isTrue_withinDuration_andFalse_onceExpired() {
        Cursor cursor = new Cursor();

        cursor.ping(1_000L);

        assertTrue(cursor.isPinging(1_000L), "the flash must be on at its starting instant");
        assertTrue(cursor.isPinging(1_000L + Cursor.PING_DURATION_MS - 1),
                "the flash must still be on just before it expires");
        assertFalse(cursor.isPinging(1_000L + Cursor.PING_DURATION_MS),
                "the flash must be off exactly when the duration elapses");
    }

    @Test
    @DisplayName("a cursor that never pinged never flashes")
    void isPinging_isFalse_whenNeverPinged() {
        Cursor cursor = new Cursor();

        assertFalse(cursor.isPinging(0L));
        assertFalse(cursor.isPinging(Long.MAX_VALUE));
    }

    @Test
    @DisplayName("pinging again restarts the flash from the new instant")
    void ping_restartsFlashFromNewInstant() {
        Cursor cursor = new Cursor();

        cursor.ping(1_000L);
        cursor.ping(5_000L);

        assertTrue(cursor.isPinging(5_000L + Cursor.PING_DURATION_MS - 1),
                "the second ping must extend the flash");
    }
}
