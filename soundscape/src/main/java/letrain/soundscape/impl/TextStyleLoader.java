package letrain.soundscape.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import letrain.soundscape.Band;
import letrain.soundscape.ClimatePreset;
import letrain.soundscape.SoundDef;
import letrain.soundscape.SoundscapeStyle;
import letrain.soundscape.StyleLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses the plain-text style format: sections with {@code key = value} lines, {@code #} comments.
 *
 * <pre>
 * [climate]             clear = rain 0.0 wind 0.1 storm 0.0
 * [sounds]              waves = sea/waves-*.wav
 * [zones]               sea   = waves, seagulls
 * [presence]            waves = 0.5 0.5 0.5 0.5 0.6 0.6 0.5   (dawn..predawn)
 * [height-by-sound]     hawks = +0.8
 * [climate-by-sound]    dogs  = 0.0 0.0 +0.2                  (rain, wind, storm)
 * [weather]             rain  = weather/rain-*.wav
 * </pre>
 */
public class TextStyleLoader implements StyleLoader {

    private static final Logger log = LoggerFactory.getLogger(TextStyleLoader.class);

    @Override
    public SoundscapeStyle load(Path path) throws IOException {
        return parse(Files.readAllLines(path, StandardCharsets.UTF_8));
    }

    @Override
    public SoundscapeStyle loadResource(String resource) throws IOException {
        try (InputStream is = TextStyleLoader.class.getResourceAsStream(resource)) {
            if (is == null) {
                throw new IOException("resource not found: " + resource);
            }
            return parse(new String(is.readAllBytes(), StandardCharsets.UTF_8).lines().toList());
        }
    }

    SoundscapeStyle parse(List<String> lines) throws IOException {
        Map<String, ClimatePreset> presets = new LinkedHashMap<>();
        Map<String, SoundDef> sounds = new LinkedHashMap<>();
        Map<String, List<String>> zones = new LinkedHashMap<>();
        Map<String, List<Float>> presence = new LinkedHashMap<>();
        Map<String, Float> height = new LinkedHashMap<>();
        Map<String, List<Float>> climate = new LinkedHashMap<>();
        Map<String, SoundDef> weather = new LinkedHashMap<>();

        String section = null;
        int lineNumber = 0;
        for (String raw : lines) {
            lineNumber++;
            String line = stripComment(raw);
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim().toLowerCase();
                continue;
            }
            if (section == null) {
                throw error(lineNumber, "line outside a section");
            }
            String key;
            String value;
            int equals = line.indexOf('=');
            if (equals >= 0) {
                key = line.substring(0, equals).trim().toLowerCase();
                value = line.substring(equals + 1).trim();
            } else {
                // Numeric tables (presence, sensitivities) are written as columns: key v1 v2 ...
                int space = firstWhitespace(line);
                if (space < 0) {
                    throw error(lineNumber, "expected 'key = value' or 'key values'");
                }
                key = line.substring(0, space).trim().toLowerCase();
                value = line.substring(space).trim();
            }
            switch (section) {
                case "climate" -> presets.put(key, parsePreset(key, value, lineNumber));
                case "sounds" -> sounds.put(key, new SoundDef(key, tokens(value)));
                case "zones" -> zones.put(key, commaList(value));
                case "presence" ->
                    presence.put(key, floats(value, Band.values().length, lineNumber));
                case "height-by-sound" -> height.put(key, singleFloat(value, lineNumber));
                case "climate-by-sound" -> climate.put(key, floats(value, 3, lineNumber));
                case "weather" -> {
                    if (!key.equals("rain") && !key.equals("wind") && !key.equals("storm")) {
                        throw error(lineNumber, "weather sound must be rain, wind or storm");
                    }
                    weather.put(key, new SoundDef(key, tokens(value)));
                }
                default -> throw error(lineNumber, "unknown section: " + section);
            }
        }
        validate(zones, sounds, presence);
        return new SoundscapeStyle(presets, sounds, zones, presence, height, climate, weather);
    }

    private void validate(Map<String, List<String>> zones, Map<String, SoundDef> sounds,
            Map<String, List<Float>> presence) throws IOException {
        for (Map.Entry<String, List<String>> zone : zones.entrySet()) {
            for (String sound : zone.getValue()) {
                if (!sounds.containsKey(sound)) {
                    throw new IOException("zone '" + zone.getKey() + "' references unknown sound '"
                            + sound + "'");
                }
            }
        }
        for (String sound : sounds.keySet()) {
            if (!presence.containsKey(sound)) {
                log.warn("sound '{}' has no presence curve and will never sound", sound);
            }
        }
    }

    private ClimatePreset parsePreset(String name, String value, int lineNumber)
            throws IOException {
        String[] parts = value.split("\\s+");
        if (parts.length % 2 != 0) {
            throw error(lineNumber, "climate preset expects 'rain X wind Y storm Z'");
        }
        float rain = 0f;
        float wind = 0f;
        float storm = 0f;
        for (int i = 0; i < parts.length; i += 2) {
            float number = singleFloat(parts[i + 1], lineNumber);
            switch (parts[i].toLowerCase()) {
                case "rain" -> rain = number;
                case "wind" -> wind = number;
                case "storm" -> storm = number;
                default -> throw error(lineNumber, "unknown climate intensity: " + parts[i]);
            }
        }
        return new ClimatePreset(name, rain, wind, storm);
    }

    private List<String> commaList(String value) {
        List<String> result = new ArrayList<>();
        for (String token : value.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private List<String> tokens(String value) {
        List<String> result = new ArrayList<>();
        for (String token : value.split("[,\\s]+")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private List<Float> floats(String value, int expected, int lineNumber) throws IOException {
        String[] parts = value.split("[,\\s]+");
        if (parts.length != expected) {
            throw error(lineNumber, "expected " + expected + " values but found " + parts.length);
        }
        List<Float> result = new ArrayList<>(expected);
        for (String part : parts) {
            result.add(singleFloat(part, lineNumber));
        }
        return result;
    }

    private float singleFloat(String value, int lineNumber) throws IOException {
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            throw error(lineNumber, "not a number: " + value);
        }
    }

    private String stripComment(String raw) {
        int comment = raw.indexOf('#');
        String line = comment >= 0 ? raw.substring(0, comment) : raw;
        return line.trim();
    }

    private int firstWhitespace(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (Character.isWhitespace(line.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private IOException error(int lineNumber, String message) {
        return new IOException("line " + lineNumber + ": " + message);
    }
}
