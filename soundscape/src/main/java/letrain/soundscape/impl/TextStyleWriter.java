package letrain.soundscape.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import letrain.soundscape.SoundGate;

/**
 * Writes a style back as text without touching the rest of the file: only the calibration sections
 * ({@code [gains]}, {@code [distance-by-sound]}, {@code [silence-when]}) are replaced, so comments
 * and formatting of the original survive the export. Values equal to the defaults are left out, and
 * an empty calibration removes its section entirely.
 */
public class TextStyleWriter {

    private static final float DEFAULT_VALUE = 1f;
    private static final float EPSILON = 1e-3f;

    /** Source lines with the [gains] section replaced by the given multipliers. */
    public List<String> withGains(List<String> sourceLines, Map<String, Float> gains) {
        return withCalibration(sourceLines, gains, Map.of(), Map.of());
    }

    /** Source lines with the three calibration sections replaced by the given values. */
    public List<String> withCalibration(List<String> sourceLines, Map<String, Float> gains,
            Map<String, Float> distance, Map<String, List<SoundGate>> gates) {
        List<String> lines = new ArrayList<>(sourceLines);
        for (String section : List.of("gains", "distance-by-sound", "silence-when")) {
            lines = stripSection(lines, section);
        }
        lines = appendValues(lines, "gains", calibrated(gains));
        lines = appendValues(lines, "distance-by-sound", calibrated(distance));
        lines = appendGates(lines, gates);
        return lines;
    }

    public void write(Path path, List<String> sourceLines, Map<String, Float> gains)
            throws IOException {
        write(path, sourceLines, gains, Map.of(), Map.of());
    }

    public void write(Path path, List<String> sourceLines, Map<String, Float> gains,
            Map<String, Float> distance, Map<String, List<SoundGate>> gates) throws IOException {
        Files.write(path, withCalibration(sourceLines, gains, distance, gates),
                StandardCharsets.UTF_8);
    }

    private List<Map.Entry<String, Float>> calibrated(Map<String, Float> values) {
        return values.entrySet().stream().filter(entry -> entry.getValue() != null
                && Math.abs(entry.getValue() - DEFAULT_VALUE) > EPSILON).toList();
    }

    private List<String> appendValues(List<String> lines, String section,
            List<Map.Entry<String, Float>> entries) {
        if (entries.isEmpty()) {
            return lines;
        }
        List<String> result = new ArrayList<>(lines);
        openSection(result, section);
        int width = entries.stream().mapToInt(entry -> entry.getKey().length()).max().orElse(0);
        for (Map.Entry<String, Float> entry : entries) {
            result.add(String.format(Locale.ROOT, "%-" + width + "s = %s", entry.getKey(),
                    format(entry.getValue())));
        }
        return result;
    }

    private List<String> appendGates(List<String> lines, Map<String, List<SoundGate>> gates) {
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, List<SoundGate>> entry : gates.entrySet()) {
            for (SoundGate gate : entry.getValue()) {
                entries.add(String.format(Locale.ROOT, "%s = %s > %s", entry.getKey(),
                        gate.variable(), format(gate.threshold())));
            }
        }
        if (entries.isEmpty()) {
            return lines;
        }
        List<String> result = new ArrayList<>(lines);
        openSection(result, "silence-when");
        result.addAll(entries);
        return result;
    }

    private void openSection(List<String> lines, String section) {
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
        if (!lines.isEmpty()) {
            lines.add("");
        }
        lines.add("[" + section + "]");
    }

    private List<String> stripSection(List<String> sourceLines, String name) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < sourceLines.size(); i++) {
            if (!isSection(sourceLines.get(i), name)) {
                lines.add(sourceLines.get(i));
                continue;
            }
            i++;
            while (i < sourceLines.size() && !isSectionHeader(clean(sourceLines.get(i)))) {
                i++;
            }
            i--;
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private boolean isSection(String line, String name) {
        String trimmed = clean(line).trim();
        return isSectionHeader(trimmed)
                && trimmed.substring(1, trimmed.length() - 1).trim().equalsIgnoreCase(name);
    }

    private boolean isSectionHeader(String trimmed) {
        return trimmed.startsWith("[") && trimmed.endsWith("]");
    }

    private String clean(String line) {
        int comment = line.indexOf('#');
        return comment >= 0 ? line.substring(0, comment) : line;
    }

    private String format(float value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        return text.endsWith("0") ? text.substring(0, text.length() - 1) : text;
    }
}
