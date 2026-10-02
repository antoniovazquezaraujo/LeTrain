package letrain.weather;

import java.util.Map;

/** Daily likelihood (0..1) of each phenomenon in a season range. */
public record WeatherProbabilities(float rain, float wind, float storm) {

    public WeatherProbabilities {
        rain = clamp(rain);
        wind = clamp(wind);
        storm = clamp(storm);
    }

    public static WeatherProbabilities from(Map<String, Float> values) {
        return new WeatherProbabilities(values.getOrDefault("rain", 0f),
                values.getOrDefault("wind", 0f), values.getOrDefault("storm", 0f));
    }

    public float of(String phenomenon) {
        return switch (phenomenon) {
            case "rain" -> rain;
            case "wind" -> wind;
            default -> storm;
        };
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
