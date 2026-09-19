package letrain.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Weather generator")
class WeatherGeneratorTest {

    private static ClimateCalendar calendar(float rain) {
        return new ClimateCalendar(List.of(new SeasonRange(1, 366,
                new WeatherProbabilities(rain, rain / 2, rain / 4))),
                new WeatherProbabilities(0f, 0f, 0f));
    }

    private static float[] run(long seed, float rain, int hours) {
        WeatherGenerator generator = new WeatherGenerator(seed);
        ClimateCalendar calendar = calendar(rain);
        float[] values = new float[hours];
        for (int hour = 0; hour < hours; hour++) {
            WeatherState state = generator.update(calendar, hour / 24 + 1, hour % 24);
            values[hour] = state.rain();
        }
        return values;
    }

    @Test
    @DisplayName("the same seed replays the same weather")
    void should_BeDeterministic() {
        float[] first = run(42L, 0.6f, 2000);
        float[] second = run(42L, 0.6f, 2000);

        for (int i = 0; i < first.length; i++) {
            assertEquals(first[i], second[i], 1e-6);
        }
    }

    @Test
    @DisplayName("different seeds give different weather")
    void should_Differ_When_SeedChanges() {
        float[] first = run(1L, 0.6f, 500);
        float[] second = run(2L, 0.6f, 500);
        boolean differs = false;
        for (int i = 0; i < first.length; i++) {
            if (Math.abs(first[i] - second[i]) > 1e-6f) {
                differs = true;
                break;
            }
        }

        assertTrue(differs);
    }

    @Test
    @DisplayName("a rainy calendar rains much more than a dry one")
    void should_RainMore_When_ProbabilityIsHigh() {
        int wet = 0;
        for (float value : run(7L, 0.9f, 8760)) {
            if (value > 0.05f) {
                wet++;
            }
        }
        int dry = 0;
        for (float value : run(7L, 0.02f, 8760)) {
            if (value > 0.05f) {
                dry++;
            }
        }

        assertTrue(wet > dry * 5, "wet " + wet + " vs dry " + dry);
    }

    @Test
    @DisplayName("intensities stay in range and ramp instead of jumping")
    void should_KeepRange_And_Ramp() {
        float[] values = run(3L, 0.8f, 3000);
        for (int i = 0; i < values.length; i++) {
            assertTrue(values[i] >= 0f && values[i] <= 1f);
            if (i > 0) {
                assertTrue(Math.abs(values[i] - values[i - 1]) <= 0.25f + 1e-6f);
            }
        }
    }
}
