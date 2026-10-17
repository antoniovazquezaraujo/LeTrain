package letrain.settings;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Save as defaults" (D4, S3a): merges the running game's effective settings into its
 * {@code letrain.cfg}. The file is rewritten line by line, so keys already present are updated in
 * place and every other line — comments, blank lines and unrelated keys — stays untouched; keys the
 * file does not know are appended under a header. The result is still a plain {@code Properties}
 * file, so the existing loader reads it back unchanged.
 */
public final class ConfigFileWriter {

    /** Header added before the keys the target file did not have. */
    static final String APPENDED_HEADER = "# Saved as defaults from the running game";

    /** File the settings dialog writes when the game found no {@code letrain.cfg} (D4). */
    public static final String DEFAULT_CONFIG_FILENAME = "letrain.cfg";

    private ConfigFileWriter() {}

    /**
     * "Save as defaults" of the in-game Settings dialog (D4, S4b #778): writes {@code values} into
     * {@code target}, or into {@value #DEFAULT_CONFIG_FILENAME} in the working directory when the
     * game found no file. Returns {@code null} on success or the failure message the dialog shows,
     * so both clients share the exact same behaviour.
     */
    public static String writeDefaultsOrMessage(File target, Map<String, String> values) {
        File resolved = target == null ? new File(DEFAULT_CONFIG_FILENAME) : target;
        try {
            writeDefaults(resolved, values);
            return null;
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    /**
     * Writes {@code values} into {@code target}, creating the file when missing. A null or empty
     * map leaves the file alone.
     *
     * @throws IllegalArgumentException when {@code target} is null
     * @throws IOException when the file cannot be read or written
     */
    public static void writeDefaults(File target, Map<String, String> values) throws IOException {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (values == null || values.isEmpty()) {
            return;
        }
        Map<String, String> pending = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key != null && value != null) {
                pending.put(key, value);
            }
        });
        List<String> lines =
                target.isFile() ? Files.readAllLines(target.toPath(), StandardCharsets.UTF_8)
                        : List.of();
        List<String> out = new ArrayList<>(lines.size() + pending.size() + 2);
        for (String line : lines) {
            String key = keyOf(line);
            if (key != null && pending.containsKey(key)) {
                out.add(key + "=" + pending.remove(key));
            } else {
                out.add(line);
            }
        }
        if (!pending.isEmpty()) {
            if (!out.isEmpty() && !out.get(out.size() - 1).isBlank()) {
                out.add("");
            }
            out.add(APPENDED_HEADER);
            pending.forEach((key, value) -> out.add(key + "=" + value));
        }
        Files.write(target.toPath(), out, StandardCharsets.UTF_8);
    }

    /**
     * Key of a {@code key=value} / {@code key:value} line, or {@code null} for comments, blank
     * lines and anything the writer must keep verbatim.
     */
    private static String keyOf(String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
            return null;
        }
        int separator = separatorIndex(trimmed);
        if (separator <= 0) {
            return null;
        }
        return trimmed.substring(0, separator).strip();
    }

    private static int separatorIndex(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '=' || c == ':') {
                return i;
            }
        }
        return -1;
    }
}
