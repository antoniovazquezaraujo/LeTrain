package letrain.soundscape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Band presence curves")
class BandTest {

    // dawn, morning, noon, afternoon, dusk, night, predawn
    private static final List<Float> CRICKETS = List.of(0.1f, 0f, 0f, 0f, 0.5f, 0.8f, 0.9f);

    @Test
    @DisplayName("returns the anchor value at an exact anchor time")
    void should_ReturnAnchorValue_When_TimeIsAnAnchor() {
        assertEquals(0.1f, Band.interpolate(CRICKETS, LocalTime.of(6, 0)), 1e-6);
        assertEquals(0.8f, Band.interpolate(CRICKETS, LocalTime.of(22, 30)), 1e-6);
        assertEquals(0.9f, Band.interpolate(CRICKETS, LocalTime.of(3, 0)), 1e-6);
    }

    @Test
    @DisplayName("interpolates between night and predawn after midnight")
    void should_Interpolate_When_BetweenNightAndPredawn() {
        // 23:30 sits a third of the way from night (22:30) to predawn (03:00).
        float expected = 0.8f + (0.9f - 0.8f) * (60f / 270f);
        assertEquals(expected, Band.interpolate(CRICKETS, LocalTime.of(23, 30)), 1e-5);
    }

    @Test
    @DisplayName("interpolates between predawn and dawn in the early morning")
    void should_Interpolate_When_BetweenPredawnAndDawn() {
        float expected = 0.9f + (0.1f - 0.9f) * (60f / 180f);
        assertEquals(expected, Band.interpolate(CRICKETS, LocalTime.of(4, 0)), 1e-5);
    }

    @Test
    @DisplayName("rejects curves that are not one value per band")
    void should_Throw_When_CurveHasWrongSize() {
        assertThrows(IllegalArgumentException.class,
                () -> Band.interpolate(List.of(0.5f, 0.5f), LocalTime.NOON));
    }
}
