package letrain.audio.material;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser for the train sound profile DSL (ADR-029 §5), in the style of the soundscape
 * {@code styles/*.sound} files: {@code key = value}, {@code #} starts a comment.
 *
 * <p>
 * Descriptor errors are never fatal: unknown ids, malformed lines and invalid metadata are logged
 * as {@code WARN} and skipped so the caller can fall back (ADR-029 §3).
 */
public final class MaterialProfileParser {

    private static final Logger log = LoggerFactory.getLogger(MaterialProfileParser.class);

    /** Fallback profile name when the descriptor does not declare one. */
    public static final String DEFAULT_PROFILE_NAME = "generic";

    private MaterialProfileParser() {}

    /** Parses profile text; null input yields an empty {@value #DEFAULT_PROFILE_NAME} profile. */
    public static MaterialProfile parse(String text) {
        if (text == null) {
            log.warn("null profile text; using empty '{}' profile", DEFAULT_PROFILE_NAME);
            return MaterialProfile.empty(DEFAULT_PROFILE_NAME);
        }
        return parse(new StringReader(text));
    }

    /**
     * Parses profile text from a reader. The reader is not closed by the parser; an
     * {@link IOException} aborts the read and returns whatever was parsed so far.
     */
    public static MaterialProfile parse(Reader reader) {
        if (reader == null) {
            log.warn("null profile reader; using empty '{}' profile", DEFAULT_PROFILE_NAME);
            return MaterialProfile.empty(DEFAULT_PROFILE_NAME);
        }
        Builder builder = new Builder();
        BufferedReader bufferedReader =
                reader instanceof BufferedReader br ? br : new BufferedReader(reader);
        try {
            String line;
            int lineNumber = 0;
            while ((line = bufferedReader.readLine()) != null) {
                lineNumber++;
                builder.line(line, lineNumber);
            }
        } catch (IOException e) {
            log.warn("error reading profile after {} materials: {}", builder.materials.size(),
                    e.getMessage());
        }
        return builder.build();
    }

    /** Mutable state for a single parse run. */
    private static final class Builder {

        private static final String KEY_PROFILE = "profile";
        private static final String KEY_PACK_VERSION = "packversion";

        private String name = DEFAULT_PROFILE_NAME;
        private int packVersion = 0;
        private final Map<MaterialId, MaterialRef> materials = new LinkedHashMap<>();

        private void line(String rawLine, int lineNumber) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) {
                return;
            }
            int equals = line.indexOf('=');
            if (equals < 0) {
                log.warn("profile line {}: missing '='; skipped: '{}'", lineNumber, line);
                return;
            }
            String key = line.substring(0, equals).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(equals + 1).trim();
            if (KEY_PROFILE.equals(key)) {
                name = value.isEmpty() ? DEFAULT_PROFILE_NAME : value;
                return;
            }
            if (KEY_PACK_VERSION.equals(key)) {
                packVersion = parsePackVersion(value, lineNumber);
                return;
            }
            Optional<MaterialId> id = MaterialId.parse(key);
            if (id.isEmpty()) {
                log.warn("profile line {}: unknown material id '{}'; skipped", lineNumber, key);
                return;
            }
            parseMaterial(id.get(), value, lineNumber);
        }

        private void parseMaterial(MaterialId id, String value, int lineNumber) {
            String[] tokens = value.split("\\s+");
            if (tokens.length == 0 || tokens[0].isEmpty()) {
                log.warn("profile line {}: material '{}' has no path; skipped", lineNumber, id);
                return;
            }
            String path = tokens[0];
            Optional<LoopPoints> loop = Optional.empty();
            double gainDb = 0.0;
            Optional<String> effort = Optional.empty();
            Optional<String> torque = Optional.empty();

            for (int i = 1; i < tokens.length; i++) {
                String token = tokens[i];
                int equals = token.indexOf('=');
                if (equals <= 0) {
                    log.warn("profile line {}: malformed metadata '{}' for '{}'; ignored",
                            lineNumber, token, id);
                    continue;
                }
                String metadataKey = token.substring(0, equals).toLowerCase(Locale.ROOT);
                String metadataValue = token.substring(equals + 1);
                switch (metadataKey) {
                    case "loop" -> loop = parseLoop(metadataValue, id, lineNumber);
                    case "gain" -> gainDb = parseGain(metadataValue, id, lineNumber);
                    case "effort" -> effort = nonEmpty(metadataValue);
                    case "torque" -> torque = nonEmpty(metadataValue);
                    default -> log.warn("profile line {}: unknown metadata '{}' for '{}'; ignored",
                            lineNumber, metadataKey, id);
                }
            }

            if (id.isLoop() && loop.isEmpty()) {
                log.warn("profile line {}: loop material '{}' without valid 'loop='; "
                        + "material dropped (fallback)", lineNumber, id);
                return;
            }
            if (!id.isLoop() && loop.isPresent()) {
                log.warn("profile line {}: one-shot '{}' declares 'loop='; loop ignored",
                        lineNumber, id);
                loop = Optional.empty();
            }
            if (materials.containsKey(id)) {
                log.warn("profile line {}: duplicate '{}'; last definition wins", lineNumber, id);
            }
            materials.put(id, new MaterialRef(path, loop, gainDb, effort, torque));
        }

        private Optional<LoopPoints> parseLoop(String value, MaterialId id, int lineNumber) {
            String[] parts = value.split(",", -1);
            if (parts.length != 2) {
                log.warn("profile line {}: '{}' loop must be <start>,<length>; got '{}'",
                        lineNumber, id, value);
                return Optional.empty();
            }
            try {
                double start = Double.parseDouble(parts[0].trim());
                double length = Double.parseDouble(parts[1].trim());
                return Optional.of(new LoopPoints(start, length));
            } catch (IllegalArgumentException e) {
                log.warn("profile line {}: invalid loop for '{}': '{}' ({})", lineNumber, id, value,
                        e.getMessage());
                return Optional.empty();
            }
        }

        private double parseGain(String value, MaterialId id, int lineNumber) {
            try {
                double gain = Double.parseDouble(value.trim());
                if (!Double.isFinite(gain)) {
                    log.warn("profile line {}: non-finite gain for '{}'; using 0.0", lineNumber,
                            id);
                    return 0.0;
                }
                return gain;
            } catch (NumberFormatException e) {
                log.warn("profile line {}: invalid gain '{}' for '{}'; using 0.0", lineNumber,
                        value, id);
                return 0.0;
            }
        }

        private int parsePackVersion(String value, int lineNumber) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                log.warn("profile line {}: invalid packVersion '{}'; using 0", lineNumber, value);
                return 0;
            }
        }

        private static Optional<String> nonEmpty(String value) {
            String trimmed = value.trim();
            return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
        }

        private static String stripComment(String line) {
            int hash = line.indexOf('#');
            return hash < 0 ? line : line.substring(0, hash);
        }

        private MaterialProfile build() {
            return new MaterialProfile(name, packVersion, materials);
        }
    }
}
