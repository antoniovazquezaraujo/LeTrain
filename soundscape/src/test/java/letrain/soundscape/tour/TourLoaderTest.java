package letrain.soundscape.tour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Tour loader")
class TourLoaderTest {

    private final TourLoader loader = new TourLoader();

    @Test
    @DisplayName("parses zones, reserved keys and comments")
    void should_ParseTour() {
        Tour tour = loader.parse(List.of("# demo", "", "0 fields=1.0",
                "10 fields=0.5 sea=0.4 enclosure=0.25 rain=0.3 weather=drizzle"));

        assertEquals(2, tour.points().size());
        assertEquals(10.0, tour.duration(), 1e-6);
        TourPoint second = tour.points().get(1);
        assertEquals(0.5f, second.zones().get("fields"), 1e-6);
        assertEquals(0.4f, second.zones().get("sea"), 1e-6);
        assertEquals(0.25f, second.enclosure(), 1e-6);
        assertEquals(0.3f, second.rain(), 1e-6);
        assertEquals("drizzle", second.weather());
    }

    @Test
    @DisplayName("rejects bad tokens and numbers")
    void should_RejectMalformedLines() {
        assertThrows(IllegalArgumentException.class, () -> loader.parse(List.of("0 fields")));
        assertThrows(IllegalArgumentException.class, () -> loader.parse(List.of("0 fields=abc")));
        assertThrows(IllegalArgumentException.class, () -> loader.parse(List.of("soon sea=1")));
        assertThrows(IllegalArgumentException.class,
                () -> loader.parse(List.of("# only comments")));
    }
}
