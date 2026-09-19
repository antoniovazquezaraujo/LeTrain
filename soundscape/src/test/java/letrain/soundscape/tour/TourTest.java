package letrain.soundscape.tour;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Tour interpolation")
class TourTest {

    private final Tour tour = new TourLoader().parse(List.of("0 fields=1.0",
            "20 sea=1.0 enclosure=1.0 weather=storm", "40 fields=1.0 enclosure=0.0"));

    @Test
    @DisplayName("returns the first point at the start")
    void should_ReturnFirstPoint() {
        TourState state = tour.stateAt(0);

        assertEquals(1f, state.zoneWeight("fields"), 1e-4);
        assertEquals(0f, state.zoneWeight("sea"), 1e-4);
    }

    @Test
    @DisplayName("interpolates zones, enclosure and weather between points")
    void should_InterpolateBetweenPoints() {
        TourState state = tour.stateAt(10);

        assertEquals(0.5f, state.zoneWeight("fields"), 1e-4);
        assertEquals(0.5f, state.zoneWeight("sea"), 1e-4);
        assertEquals(0.5f, state.enclosure(), 1e-4);
        assertEquals("storm", state.weather());
    }

    @Test
    @DisplayName("wraps around the duration")
    void should_WrapAround() {
        TourState state = tour.stateAt(45);

        assertEquals(0.75f, state.zoneWeight("fields"), 1e-4);
        assertEquals(0.25f, state.zoneWeight("sea"), 1e-4);
    }

    @Test
    @DisplayName("clamps before the first point and after the last")
    void should_ClampOutsideRange() {
        assertEquals(1f, tour.stateAt(-5).zoneWeight("fields"), 1e-4);
        assertEquals(1f, tour.stateAt(40).zoneWeight("fields"), 1e-4);
    }
}
