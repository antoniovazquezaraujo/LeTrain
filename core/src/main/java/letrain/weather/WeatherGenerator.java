package letrain.weather;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Deterministic hourly weather (ADR-027). Every game hour it checks the season probabilities to
 * start or stop each phenomenon, keeps phenomena active for a minimum of one hour and ramps the
 * intensities instead of jumping. Same seed and same (day, hour) sequence give the same weather, so
 * command journals replay identically.
 */
public class WeatherGenerator {

    private static final String[] PHENOMENA = {"rain", "wind", "storm"};
    private static final float START_RATE = 0.15f;
    private static final float RAMP_PER_HOUR = 0.25f;
    private static final int MAX_ACTIVE_HOURS = 12;

    private final Random random;
    private final Map<String, Phenomenon> phenomena = new LinkedHashMap<>();

    public WeatherGenerator(long seed) {
        this.random = new Random(seed);
        for (String name : PHENOMENA) {
            phenomena.put(name, new Phenomenon());
        }
    }

    public WeatherState update(ClimateCalendar calendar, int dayOfYear, int hour) {
        WeatherProbabilities probabilities = calendar.probabilitiesOn(dayOfYear);
        Map<String, Float> intensities = new LinkedHashMap<>();
        for (String name : PHENOMENA) {
            intensities.put(name, advance(name, probabilities.of(name)));
        }
        return new WeatherState(intensities.get("rain"), intensities.get("wind"),
                intensities.get("storm"));
    }

    private float advance(String name, float probability) {
        Phenomenon phenomenon = phenomena.get(name);
        if (phenomenon.active) {
            phenomenon.remainingHours--;
            if (phenomenon.remainingHours <= 0) {
                phenomenon.active = false;
                phenomenon.target = 0f;
            } else {
                phenomenon.target = drawIntensity(probability);
            }
        } else if (probability > 0f && random.nextFloat() < probability * START_RATE) {
            phenomenon.active = true;
            phenomenon.remainingHours =
                    1 + random.nextInt(Math.max(1, Math.round(1 + MAX_ACTIVE_HOURS * probability)));
            phenomenon.target = drawIntensity(probability);
        } else {
            phenomenon.target = 0f;
        }
        if (phenomenon.intensity < phenomenon.target) {
            phenomenon.intensity =
                    Math.min(phenomenon.target, phenomenon.intensity + RAMP_PER_HOUR);
        } else {
            phenomenon.intensity =
                    Math.max(phenomenon.target, phenomenon.intensity - RAMP_PER_HOUR);
        }
        return phenomenon.intensity;
    }

    private float drawIntensity(float probability) {
        return probability * (0.5f + random.nextFloat() * 0.7f);
    }

    private static final class Phenomenon {

        private boolean active;
        private int remainingHours;
        private float target;
        private float intensity;
    }
}
