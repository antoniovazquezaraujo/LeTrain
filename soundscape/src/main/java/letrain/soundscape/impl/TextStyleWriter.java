package letrain.soundscape.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes a style back as text without touching the rest of the file: only the {@code [gains]}
 * section is replaced, so comments and formatting of the original survive the export. Gains equal
 * to 1.0 are left out, and an empty calibration removes the section entirely.
 */
public class TextStyleWriter {

    private static final String SECTION = "[gains]";
    private static final float DEFAULT_GAIN = 1f;
    private static final float EPSILON = 1e-3f;

    /** Source lines with the {@code [gains]} section replaced by the given multipliers. */
    public List<String> withGains(List<String> sourceLines, Map<String, Float> gains) {
        List<String> lines = new ArrayList<>(stripGainsSection(sourceLines));
        List<Map.Entry<String, Float>> entries = calibrated(gains);
        if (entries.isEmpty()) {
            return lines;
        }
        if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
            lines.add("");
        }
        lines.add(SECTION);
        int width = entries.stream().mapToInt(entry -> entry.getKey().length()).max().orElse(0);
        for (Map.Entry<String, Float> entry : entries) {
            lines.add(String.format(Locale.ROOT, "%-" + width + "s = %s", entry.getKey(),
                    format(entry.getValue())));
        }
        return lines;
    }

    public void write(Path path, List<String> sourceLines, Map<String, Float> gains)
            throws IOException {
        Files.write(path, withGains(sourceLines, gains), StandardCharsets.UTF_8);
    }

    private List<Map.Entry<String, Float>> calibrated(Map<String, Float> gains) {
        return gains.entrySet().stream().filter(entry -> entry.getValue() != null
                && Math.abs(entry.getValue() - DEFAULT_GAIN) > EPSILON).toList();
    }

    private List<String> stripGainsSection(List<String> sourceLines) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < sourceLines.size(); i++) {
            if (!isSection(sourceLines.get(i), "gains")) {
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
