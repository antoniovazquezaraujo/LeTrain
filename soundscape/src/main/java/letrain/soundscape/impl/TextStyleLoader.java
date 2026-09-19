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
import letrain.soundscape.SeasonRange;
import letrain.soundscape.SoundDef;
import letrain.soundscape.SoundGate;
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
 * [gains]               cicadas = 0.6                         (1.0 = unchanged)
 * [distance-by-sound]        hawks = 0.3                           (0 = at the listener)
 * [silence-when]        crickets = rain > 0.25                 (behaviour gates)
 * </pre>
 */
public class TextStyleLoader implements StyleLoader {

    private static final Logger log = LoggerFactory.getLogger(TextStyleLoader.class);

    @Override
    public SoundscapeStyle load(Path path) throws IOException {
        return parse(readLines(path));
    }

    @Override
    public SoundscapeStyle loadResource(String resource) throws IOException {
        return parse(readResourceLines(resource));
    }

    /** Raw text of a style file, kept for exports that must preserve comments and layout. */
    public List<String> readLines(Path path) throws IOException {
        return Files.readAllLines(path, StandardCharsets.UTF_8);
    }

    /** Raw text of a bundled style, kept for exports that must preserve comments and layout. */
    public List<String> readResourceLines(String resource) throws IOException {
        try (InputStream is = TextStyleLoader.class.getResourceAsStream(resource)) {
            if (is == null) {
                throw new IOException("resource not found: " + resource);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }

    public SoundscapeStyle parse(List<String> lines) throws IOException {
        Map<String, ClimatePreset> presets = new LinkedHashMap<>();
        List<SeasonRange> seasons = new ArrayList<>();
        Map<String, Float> seasonDefaults = new LinkedHashMap<>();
        Map<String, SoundDef> sounds = new LinkedHashMap<>();
        Map<String, List<String>> zones = new LinkedHashMap<>();
        Map<String, List<Float>> presence = new LinkedHashMap<>();
        Map<String, Float> height = new LinkedHashMap<>();
        Map<String, List<Float>> climate = new LinkedHashMap<>();
        Map<String, SoundDef> weather = new LinkedHashMap<>();
        Map<String, SoundDef> heightSounds = new LinkedHashMap<>();
        Map<String, Float> gains = new LinkedHashMap<>();
        Map<String, Float> distance = new LinkedHashMap<>();
        Map<String, List<SoundGate>> gates = new LinkedHashMap<>();

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
            if ("seasons".equals(section)) {
                SeasonRange range = parseSeason(line, lineNumber, seasonDefaults);
                if (range != null) {
                    seasons.add(range);
                }
                continue;
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
                case "height" -> heightSounds.put(key, new SoundDef(key, tokens(value)));
                case "weather" -> {
                    if (!key.equals("rain") && !key.equals("wind") && !key.equals("storm")) {
                        throw error(lineNumber, "weather sound must be rain, wind or storm");
                    }
                    weather.put(key, new SoundDef(key, tokens(value)));
                }
                case "gains" -> gains.put(key, singleFloat(value, lineNumber));
                case "distance-by-sound" -> distance.put(key, singleFloat(value, lineNumber));
                case "silence-when" -> gates.computeIfAbsent(key, sound -> new ArrayList<>())
                        .add(parseGate(value, lineNumber));
                default -> throw error(lineNumber, "unknown section: " + section);
            }
        }
        validate(zones, sounds, presence);
        validateSeasons(seasons);
        validateGains(gains, sounds, weather, heightSounds);
        validateGains(distance, sounds, weather, heightSounds);
        validateGates(gates, sounds);
        return new SoundscapeStyle(presets, seasons, seasonDefaults, sounds, zones, presence,
                height, climate, weather, heightSounds, gains, distance, gates);
    }


    private static final String[] MONTHS = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug",
            "sep", "oct", "nov", "dec"};
    private static final int[] MONTH_START = {0, 1, 32, 60, 91, 121, 152, 182, 213, 244, 274, 305,
            335};
    private static final int[] MONTH_DAYS = {0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    private SeasonRange parseSeason(String line, int number, Map<String, Float> defaults)
            throws IOException {
        String clean = line.trim();
        if (clean.toLowerCase().startsWith("default")) {
            defaults.putAll(parseSeasonValues(clean.substring("default".length()), number));
            return null;
        }
        String[] sides = clean.split("\\s+to\\s+", 2);
        if (sides.length != 2) {
            throw error(number, "expected '<Month>[-<day>] to <Month>[-<day>] rain=… wind=… storm=…'");
        }
        String[] right = sides[1].trim().split("\\s+");
        int start = parseMonthDay(sides[0].trim(), number, true);
        int end = parseMonthDay(right[0], number, false);
        String values = sides[1].trim().substring(right[0].length());
        return new SeasonRange(start, end, parseSeasonValues(values, number));
    }

    private Map<String, Float> parseSeasonValues(String text, int number) throws IOException {
        Map<String, Float> probabilities = new LinkedHashMap<>();
        for (String token : text.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            int equals = token.indexOf('=');
            if (equals <= 0) {
                throw error(number, "expected phenomenon=value, got '" + token + "'");
            }
            String name = token.substring(0, equals).trim().toLowerCase();
            if (!name.equals("rain") && !name.equals("wind") && !name.equals("storm")) {
                throw error(number, "unknown phenomenon '" + name + "'");
            }
            probabilities.put(name, singleFloat(token.substring(equals + 1), number));
        }
        return probabilities;
    }

    private int parseMonthDay(String token, int number, boolean start) throws IOException {
        String clean = token.trim().toLowerCase();
        int dash = clean.indexOf('-');
        String monthName = dash < 0 ? clean : clean.substring(0, dash);
        int month = -1;
        for (int i = 0; i < MONTHS.length; i++) {
            if (MONTHS[i].equals(monthName)) {
                month = i + 1;
                break;
            }
        }
        if (month < 0) {
            throw error(number, "unknown month '" + monthName + "'");
        }
        int day = MONTH_DAYS[month];
        if (dash >= 0) {
            try {
                day = Integer.parseInt(clean.substring(dash + 1));
            } catch (NumberFormatException e) {
                throw error(number, "bad day in '" + token + "'");
            }
            if (day < 1 || day > MONTH_DAYS[month]) {
                throw error(number, "day out of range in '" + token + "'");
            }
        } else if (start) {
            day = 1;
        }
        return MONTH_START[month] + day - 1;
    }

    private void validateSeasons(List<SeasonRange> seasons) throws IOException {
        for (int i = 0; i < seasons.size(); i++) {
            for (int j = i + 1; j < seasons.size(); j++) {
                if (overlaps(seasons.get(i), seasons.get(j))) {
                    throw error(0, "season ranges overlap: " + range(seasons.get(i)) + " and "
                            + range(seasons.get(j)));
                }
            }
        }
    }

    private boolean overlaps(SeasonRange left, SeasonRange right) {
        for (int day = 1; day <= 366; day++) {
            if (contains(left, day) && contains(right, day)) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(SeasonRange range, int dayOfYear) {
        int day = ((dayOfYear - 1) % 366 + 366) % 366 + 1;
        if (range.startDay() <= range.endDay()) {
            return day >= range.startDay() && day <= range.endDay();
        }
        return day >= range.startDay() || day <= range.endDay();
    }

    private String range(SeasonRange range) {
        return range.startDay() + ".." + range.endDay();
    }

    private SoundGate parseGate(String value, int lineNumber) throws IOException {
        String[] parts = value.split(">", 2);
        if (parts.length != 2) {
            throw error(lineNumber, "silence gate expects 'rain > 0.25'");
        }
        String variable = parts[0].trim().toLowerCase();
        if (!variable.equals("rain") && !variable.equals("wind") && !variable.equals("storm")) {
            throw error(lineNumber, "unknown gate variable: " + parts[0].trim());
        }
        return new SoundGate(variable, singleFloat(parts[1], lineNumber));
    }

    private void validateGates(Map<String, List<SoundGate>> gates, Map<String, SoundDef> sounds) {
        for (String sound : gates.keySet()) {
            if (!sounds.containsKey(sound)) {
                log.warn("silence gate for unknown sound '{}' is ignored", sound);
            }
        }
    }

    /**
     * Gain keys may name a catalog sound or a provided one ({@code weather-*}, {@code height-*}).
     */
    private void validateGains(Map<String, Float> gains, Map<String, SoundDef> sounds,
            Map<String, SoundDef> weather, Map<String, SoundDef> heightSounds) {
        for (String sound : gains.keySet()) {
            boolean known = sounds.containsKey(sound)
                    || (sound.startsWith("weather-")
                            && weather.containsKey(sound.substring("weather-".length())))
                    || (sound.startsWith("height-")
                            && heightSounds.containsKey(sound.substring("height-".length())));
            if (!known) {
                log.warn("gain for unknown sound '{}' is ignored", sound);
            }
        }
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
