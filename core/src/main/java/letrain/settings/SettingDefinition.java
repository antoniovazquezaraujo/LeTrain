package letrain.settings;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Optional;

/**
 * Metadata of one setting: everything the new-game / settings dialogs need to render and validate a
 * field, with no UI dependency. The catalogue ({@link SettingsCatalog}) is data only, so it can be
 * asserted by tests and consumed by both clients and the scenario editor.
 *
 * @param key stable configuration key, as read from {@code letrain.cfg}, a scenario's
 *        {@code configuration { }} block or a savegame (e.g. {@code terrain.waterPercent})
 * @param category form section the field belongs to
 * @param displayName human-readable label for the dialogs
 * @param type value grammar
 * @param unit optional unit shown next to the field ("%", "s", "°"...); may be null
 * @param min optional inclusive lower bound; may be null when unbounded
 * @param max optional inclusive upper bound; may be null when unbounded
 * @param defaultValue default value as text; never null ({@code "random"} for the seed)
 * @param applyState when the value can be applied (LIVE / REAPPLY / NEW_GAME)
 */
public record SettingDefinition(String key, SettingCategory category, String displayName,
        SettingValueType type, String unit, Double min, Double max, String defaultValue,
        SettingApplyState applyState) {

    public SettingDefinition {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(defaultValue, "defaultValue must not be null");
        Objects.requireNonNull(applyState, "applyState must not be null");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (min != null && max != null && min > max) {
            throw new IllegalArgumentException(
                    "invalid range for '" + key + "': min " + min + " > max " + max);
        }
    }

    /** Inclusive lower bound, empty when the setting is unbounded below. */
    public OptionalDouble minValue() {
        return min == null ? OptionalDouble.empty() : OptionalDouble.of(min);
    }

    /** Inclusive upper bound, empty when the setting is unbounded above. */
    public OptionalDouble maxValue() {
        return max == null ? OptionalDouble.empty() : OptionalDouble.of(max);
    }

    /** Unit shown next to the field, empty when the value is unit-less. */
    public Optional<String> unitValue() {
        return Optional.ofNullable(unit);
    }
}
