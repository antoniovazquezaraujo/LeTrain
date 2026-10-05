package letrain.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Cursor locate ping (#696)")
class CursorTest {

    @Test
    @DisplayName("an untouched cursor never pings")
    void should_NotPing_When_NeverStarted() {
        Cursor cursor = new Cursor();

        assertFalse(cursor.isPinging(0L));
        assertFalse(cursor.isPinging(123456789L));
        assertEquals(-1f, cursor.pingProgress(0L));
    }

    @Test
    @DisplayName("a ping is active from its start until PING_DURATION_MS")
    void should_Ping_WithinDuration() {
        Cursor cursor = new Cursor();

        cursor.ping(1_000L);

        assertTrue(cursor.isPinging(1_000L));
        assertTrue(cursor.isPinging(1_000L + Cursor.PING_DURATION_MS / 2));
        assertTrue(cursor.isPinging(1_000L + Cursor.PING_DURATION_MS - 1));
        assertFalse(cursor.isPinging(1_000L + Cursor.PING_DURATION_MS));
        assertFalse(cursor.isPinging(1_000L + Cursor.PING_DURATION_MS + 50));
    }

    @Test
    @DisplayName("progress sweeps once from 0 to 1 and is -1 when idle")
    void should_SweepProgressOnce() {
        Cursor cursor = new Cursor();
        cursor.ping(500L);

        assertEquals(0f, cursor.pingProgress(500L));
        assertEquals(0.5f, cursor.pingProgress(500L + Cursor.PING_DURATION_MS / 2), 1e-6);
        assertTrue(cursor.pingProgress(500L + Cursor.PING_DURATION_MS - 1) > 0.99f);
        assertEquals(-1f, cursor.pingProgress(500L + Cursor.PING_DURATION_MS));
    }

    @Test
    @DisplayName("pinging again restarts the sweep")
    void should_Restart_When_PingCalledAgain() {
        Cursor cursor = new Cursor();
        cursor.ping(0L);
        cursor.ping(10_000L);

        assertFalse(cursor.isPinging(10_000L - 1));
        assertTrue(cursor.isPinging(10_000L));
        assertEquals(0f, cursor.pingProgress(10_000L));
    }

    @Test
    @DisplayName("a backwards clock jump never reports a ping")
    void should_NotPing_When_ClockGoesBackwards() {
        Cursor cursor = new Cursor();
        cursor.ping(10_000L);

        assertFalse(cursor.isPinging(9_999L));
        assertEquals(-1f, cursor.pingProgress(9_999L));
    }
}
