package letrain.track.rail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ForkRailTrackTest {
       @ParameterizedTest(name = "fromInt({0}) should return {1}")
    @CsvSource({
            "0, E",
            "1, NE",
            "2, N",
            "3, NW",
            "4, W",
            "5, SW",
            "6, S",
            "7, SE",
            "8, E",
            "-1, SE",
            "-2, S",
            "-3, SW",
            "-4, W",
            "-5, NW",
            "-6, N",
            "-7, NE",
            "-8, E"
    })
    void testAddRoute() {

    }
}
