package letrain.map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DirTest {
    private Dir dir;

    @BeforeEach
    void setUp() {
        dir = Dir.E;
    }

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
    void testFromInt(int a, Dir b) {
        Assertions.assertEquals(b, Dir.fromInt(a));
    }

    @ParameterizedTest(name = "add({0}, {1}) should return {2}")
    @CsvSource({
            "0, E, E",
            "1, E, NE",
            "2, E, N",
            "3, E, NW",
            "4, E, W",
            "5, E, SW",
            "6, E, S",
            "7, E, SE",
            "8, E, E",
            "-1, E, SE",
            "-2, E, S",
            "-3, E, SW",
            "-4, E, W",
            "-5, E, NW",
            "-6, E, N",
            "-7, E, NE",
            "-8, E, E"
    })
    void testAdd(int a, Dir dir, Dir expected) {
        Assertions.assertEquals(expected, Dir.add(dir, a));
    }

    @ParameterizedTest(name = "isValidValue({0}) should return {1}")
    @CsvSource({
            "0, true",
            "1, true",
            "2, true",
            "3, true",
            "4, true",
            "5, true",
            "6, true",
            "7, true",
            "8, false",
            "-1, false",
            "-2, false",
            "-3, false",
            "-4, false",
            "-5, false",
            "-6, false",
            "-7, false",
            "-8, false"
    })
    void testIsValidValue(int a, boolean expected) {
        Assertions.assertEquals(expected, Dir.isValidValue(a));
    }

    @ParameterizedTest(name = "invert({0}) should return {1}")
    @CsvSource({
            "W, E",
            "SW, NE",
            "S, N",
            "SE, NW",
            "E, W",
            "NE, SW",
            "N, S",
            "NW, SE"
    })
    void testInvert(Dir a, Dir expected) {
        Assertions.assertEquals(expected, Dir.invert(a));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, 1",
            "2, 2",
            "3, 3",
            "4, 4",
            "5, -3",
            "6, -2",
            "7, -1",
            "-1, -1",
            "-2, -2",
            "-3, -3",
            "-4, -4",
            "-5, 3",
            "-6, 2",
            "-7, 1"
    })
    void testShortWay(int a, int b) {
        Assertions.assertEquals(b, Dir.shortWay(a));
    }

    @ParameterizedTest
    @CsvSource({
            "E, 0",
            "NE, 1",
            "N, 2",
            "NW, 3",
            "W, 4",
            "SW, -3",
            "S, -2",
            "SE, -1"
    })
    void testAngularDistance(Dir a, int b) {
        Assertions.assertEquals(b, dir.angularDistance(a));
    }

    @ParameterizedTest
    @CsvSource({
            "W, E",
            "SW, NE",
            "S, N",
            "SE, NW",
            "E, W",
            "NE, SW",
            "N, S",
            "NW, SE"
    })
    void testInverse(Dir a, Dir b) {
        Assertions.assertEquals(b, a.inverse());
    }

    @ParameterizedTest
    @CsvSource({
            "SE, E",
            "E, NE",
            "NE, N",
            "N, NW",
            "NW, W",
            "W, SW",
            "SW, S",
            "S, SE"
    })
    void testTurnLeft(Dir a, Dir b) {
        Assertions.assertEquals(b, a.turnLeft());
    }

    @ParameterizedTest
    @CsvSource({
            "E, SE",
            "NE, E",
            "N, NE",
            "NW, N",
            "W, NW",
            "SW, W",
            "S, SW",
            "SE, S"
    })
    void testTurnRight(Dir a, Dir b) {
        Assertions.assertEquals(b, a.turnRight());
    }

    @ParameterizedTest
    @CsvSource({
            "E, false",
            "NE, false",
            "N, false",
            "NW, true",
            "W, false",
            "SW, true",
            "S, false",
            "SE, false"
    })
    void testIsCurve(Dir a, boolean b) {
        Assertions.assertEquals(b, dir.isCurve(a));
    }

    @ParameterizedTest
    @CsvSource({
            "E, false",
            "NE, false",
            "N, false",
            "NW, false",
            "W, true",
            "SW, false",
            "S, false",
            "SE, false"
    })
    void testIsStraight(Dir a, boolean b) {
        Dir d = Dir.E;
        Assertions.assertEquals(b, d.isStraight(a));
        Assertions.assertTrue(a.isStraight(a.inverse()));
    }

    @Test
    void testToString() {
        Assertions.assertEquals("E", dir.toString());
    }
}