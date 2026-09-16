package letrain.soundscape.gui;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import letrain.soundscape.ClimatePreset;
import letrain.soundscape.CompositionInput;
import letrain.soundscape.SoundscapeStyle;

/**
 * Editable ambient state behind the test GUI: time, zone weights, height and weather. Pure data and
 * clamping, so the interesting behaviour can be unit-tested without opening a window.
 */
public class PlayerState {

    private static final int MINUTES_PER_DAY = 24 * 60;

    private final SoundscapeStyle style;
    private final Map<String, Float> zoneWeights = new LinkedHashMap<>();
    private int minuteOfDay = 12 * 60;
    private float height;
    private float rain;
    private float wind;
    private float storm;

    public PlayerState(SoundscapeStyle style) {
        this.style = style;
        for (String zone : style.zones().keySet()) {
            zoneWeights.put(zone, defaultWeight(zone));
        }
    }

    private float defaultWeight(String zone) {
        return switch (zone) {
            case "fields" -> 0.7f;
            case "sea" -> 0.5f;
            default -> 0f;
        };
    }

    public SoundscapeStyle style() {
        return style;
    }

    public int minuteOfDay() {
        return minuteOfDay;
    }

    public void setMinuteOfDay(int minute) {
        this.minuteOfDay = Math.floorMod(minute, MINUTES_PER_DAY);
    }

    public void advance(int minutes) {
        setMinuteOfDay(minuteOfDay + minutes);
    }

    public LocalTime time() {
        return LocalTime.of(minuteOfDay / 60, minuteOfDay % 60);
    }

    public float height() {
        return height;
    }

    public void setHeight(float height) {
        this.height = clamp01(height);
    }

    public Map<String, Float> zoneWeights() {
        return Map.copyOf(zoneWeights);
    }

    public void setZoneWeight(String zone, float weight) {
        if (zoneWeights.containsKey(zone)) {
            zoneWeights.put(zone, clamp01(weight));
        }
    }

    public float rain() {
        return rain;
    }

    public float wind() {
        return wind;
    }

    public float storm() {
        return storm;
    }

    public void setWeather(float rain, float wind, float storm) {
        this.rain = clamp01(rain);
        this.wind = clamp01(wind);
        this.storm = clamp01(storm);
    }

    public void applyPreset(ClimatePreset preset) {
        setWeather(preset.rain(), preset.wind(), preset.storm());
    }

    public CompositionInput toInput() {
        return new CompositionInput(time(), zoneWeights, height, rain, wind, storm);
    }

    private float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
