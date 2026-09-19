package letrain.soundscape.tour;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TourLoader {

    public Tour load(Path path) throws IOException {
        return parse(Files.readAllLines(path, StandardCharsets.UTF_8));
    }

    public Tour parse(List<String> lines) {
        List<TourPoint> points = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            TourPoint point = parseLine(lines.get(i), i + 1);
            if (point != null) {
                points.add(point);
            }
        }
        if (points.isEmpty()) {
            throw new IllegalArgumentException("a tour needs at least one line");
        }
        return new Tour(points);
    }

    private TourPoint parseLine(String line, int number) {
        String clean = line.trim();
        if (clean.isEmpty() || clean.startsWith("#")) {
            return null;
        }
        String[] tokens = clean.split("\\s+");
        double seconds;
        try {
            seconds = Double.parseDouble(tokens[0]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "line " + number + ": expected seconds, got '" + tokens[0] + "'");
        }
        Map<String, Float> zones = new LinkedHashMap<>();
        float enclosure = 0f;
        float rain = 0f;
        float wind = 0f;
        float storm = 0f;
        String weather = null;
        for (int i = 1; i < tokens.length; i++) {
            int equals = tokens[i].indexOf('=');
            if (equals <= 0) {
                throw new IllegalArgumentException(
                        "line " + number + ": expected key=value, got '" + tokens[i] + "'");
            }
            String key = tokens[i].substring(0, equals);
            String value = tokens[i].substring(equals + 1);
            switch (key) {
                case "enclosure" -> enclosure = floatValue(value, key, number);
                case "rain" -> rain = floatValue(value, key, number);
                case "wind" -> wind = floatValue(value, key, number);
                case "storm" -> storm = floatValue(value, key, number);
                case "weather" -> weather = value;
                default -> zones.put(key, floatValue(value, key, number));
            }
        }
        return new TourPoint(seconds, zones, enclosure, rain, wind, storm, weather);
    }

    private float floatValue(String value, String key, int number) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "line " + number + ": " + key + " expects a number, got '" + value + "'");
        }
    }
}
