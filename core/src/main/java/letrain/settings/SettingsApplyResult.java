package letrain.settings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Outcome of applying settings to a running game (S3a, concept §5.2): the keys that reached the
 * game, in request order, and for every rejected key the reason the settings dialog shows.
 *
 * @param applied keys effectively applied to the running game
 * @param rejections rejected key → human-readable reason (invalid value, NEW_GAME, scenario lock)
 */
public record SettingsApplyResult(Set<String> applied, Map<String, String> rejections) {

    /** Reason for a key whose apply state only takes effect when the world is created. */
    public static final String REQUIRES_NEW_GAME = "requires a new game";

    /** Reason for a key fixed by the scenario's {@code configuration { }} (D10). */
    public static final String DEFINED_BY_SCENARIO = "defined by scenario";

    /** Reason for a key the catalogue does not know. */
    public static final String UNKNOWN_SETTING = "unknown setting";

    public SettingsApplyResult {
        applied = Collections.unmodifiableSet(new LinkedHashSet<>(applied));
        rejections = Collections.unmodifiableMap(new LinkedHashMap<>(rejections));
    }

    /** True when at least one key was refused and its reason must be shown. */
    public boolean hasRejections() {
        return !rejections.isEmpty();
    }
}
