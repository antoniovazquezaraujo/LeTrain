package letrain.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The single settings catalogue (concept §1): key, display name, type, range/unit, default and
 * apply state for every v1 setting (terrain, world, economy, construction, UI). Pure data, no UI
 * and no engine dependencies, so the new-game dialog, the in-game settings dialog, the scenario
 * editor and the {@code letrain.cfg} loader can all be driven from here.
 *
 * <p>
 * Validation returns a normalised value or throws {@link IllegalArgumentException} with a
 * descriptive message. Cross-field rules (water + mountain ≤ {@value #MAX_TERRAIN_PERCENT} %) are
 * checked by {@link #validateAll(Map)}.
 */
public final class SettingsCatalog {

    /** Minimum land left for the world: water + mountain must stay ≤ 95 % (concept §3.1). */
    public static final int MIN_FIELD_PERCENT = 5;

    /** Maximum water or mountain percentage when combined with the minimum field. */
    public static final int MAX_TERRAIN_PERCENT = 100 - MIN_FIELD_PERCENT;

    /** Default water percentage (D2). */
    public static final int DEFAULT_WATER_PERCENT = 25;

    /** Default mountain percentage (D2). */
    public static final int DEFAULT_MOUNTAIN_PERCENT = 10;

    private static final List<SettingDefinition> DEFINITIONS = List.of(
            new SettingDefinition("seed", SettingCategory.TERRAIN, "Seed", SettingValueType.SEED,
                    null, null, null, "random", SettingApplyState.NEW_GAME),
            new SettingDefinition("terrain.waterPercent", SettingCategory.TERRAIN, "Water",
                    SettingValueType.INTEGER, "%", 0.0, (double) MAX_TERRAIN_PERCENT,
                    String.valueOf(DEFAULT_WATER_PERCENT), SettingApplyState.NEW_GAME),
            new SettingDefinition("terrain.mountainPercent", SettingCategory.TERRAIN, "Mountain",
                    SettingValueType.INTEGER, "%", 0.0, (double) MAX_TERRAIN_PERCENT,
                    String.valueOf(DEFAULT_MOUNTAIN_PERCENT), SettingApplyState.NEW_GAME),

            new SettingDefinition("world.latitude", SettingCategory.WORLD, "Latitude",
                    SettingValueType.DECIMAL, "°", -90.0, 90.0, "40.0", SettingApplyState.REAPPLY),
            new SettingDefinition("time.dayDurationSeconds", SettingCategory.WORLD, "Day duration",
                    SettingValueType.INTEGER, "s", 60.0, 86400.0, "1440", SettingApplyState.LIVE),
            new SettingDefinition("map.VIEW_RADIUS", SettingCategory.WORLD, "View radius",
                    SettingValueType.INTEGER, "cells", 5.0, null, "15", SettingApplyState.REAPPLY),

            new SettingDefinition("startingBalance", SettingCategory.ECONOMY, "Starting balance",
                    SettingValueType.DECIMAL, null, 0.0, null, "0.0", SettingApplyState.LIVE),
            new SettingDefinition("fuelCostPerMeter", SettingCategory.ECONOMY,
                    "Fuel cost per meter", SettingValueType.DECIMAL, null, 0.0, null, "0.5",
                    SettingApplyState.LIVE),
            new SettingDefinition("cargoLoadingFee", SettingCategory.ECONOMY, "Cargo loading fee",
                    SettingValueType.DECIMAL, null, 0.0, null, "100.0", SettingApplyState.LIVE),
            new SettingDefinition("cargo.GOLD", SettingCategory.ECONOMY, "Gold cargo value",
                    SettingValueType.DECIMAL, null, 0.0, null, "2000.0", SettingApplyState.LIVE),
            new SettingDefinition("cargo.COAL", SettingCategory.ECONOMY, "Coal cargo value",
                    SettingValueType.DECIMAL, null, 0.0, null, "200.0", SettingApplyState.LIVE),
            new SettingDefinition("cargo.RUBY", SettingCategory.ECONOMY, "Ruby cargo value",
                    SettingValueType.DECIMAL, null, 0.0, null, "20000.0", SettingApplyState.LIVE),
            new SettingDefinition("price.CONSTRUCTED_NORMAL_RAIL_TRACK", SettingCategory.ECONOMY,
                    "Build normal track", SettingValueType.DECIMAL, null, 0.0, null, "100.0",
                    SettingApplyState.LIVE),
            new SettingDefinition("price.CONSTRUCTED_BRIDGE_RAIL_TRACK", SettingCategory.ECONOMY,
                    "Build bridge", SettingValueType.DECIMAL, null, 0.0, null, "20000.0",
                    SettingApplyState.LIVE),
            new SettingDefinition("price.CONSTRUCTED_TUNNEL_RAIL_TRACK", SettingCategory.ECONOMY,
                    "Build tunnel", SettingValueType.DECIMAL, null, 0.0, null, "70000.0",
                    SettingApplyState.LIVE),
            new SettingDefinition("price.CONSTRUCTED_FORK", SettingCategory.ECONOMY, "Build fork",
                    SettingValueType.DECIMAL, null, 0.0, null, "1000.0", SettingApplyState.LIVE),
            new SettingDefinition("price.CONSTRUCTED_STATION", SettingCategory.ECONOMY,
                    "Build station", SettingValueType.DECIMAL, null, 0.0, null, "100000.0",
                    SettingApplyState.LIVE),
            new SettingDefinition("price.CONSTRUCTED_SENSOR", SettingCategory.ECONOMY,
                    "Build sensor", SettingValueType.DECIMAL, null, 0.0, null, "100.0",
                    SettingApplyState.LIVE),
            new SettingDefinition("price.CONSTRUCTED_SEMAPHORE", SettingCategory.ECONOMY,
                    "Build semaphore", SettingValueType.DECIMAL, null, 0.0, null, "100.0",
                    SettingApplyState.LIVE),
            new SettingDefinition("price.CONSTRUCTED_LOCOMOTIVE", SettingCategory.ECONOMY,
                    "Build locomotive", SettingValueType.DECIMAL, null, 0.0, null, "50000.0",
                    SettingApplyState.LIVE),
            new SettingDefinition("price.CONSTRUCTED_WAGON", SettingCategory.ECONOMY, "Build wagon",
                    SettingValueType.DECIMAL, null, 0.0, null, "30000.0", SettingApplyState.LIVE),
            new SettingDefinition("price.DESTROYED_NORMAL_RAIL_TRACK", SettingCategory.ECONOMY,
                    "Remove normal track", SettingValueType.DECIMAL, null, 0.0, null, "500.0",
                    SettingApplyState.LIVE),
            new SettingDefinition("price.DESTROYED_BRIDGE_RAIL_TRACK", SettingCategory.ECONOMY,
                    "Remove bridge", SettingValueType.DECIMAL, null, 0.0, null, "10000.0",
                    SettingApplyState.LIVE),
            new SettingDefinition("price.DESTROYED_TUNNEL_RAIL_TRACK", SettingCategory.ECONOMY,
                    "Remove tunnel", SettingValueType.DECIMAL, null, 0.0, null, "30000.0",
                    SettingApplyState.LIVE),
            new SettingDefinition("price.DESTROYED_FORK", SettingCategory.ECONOMY, "Remove fork",
                    SettingValueType.DECIMAL, null, 0.0, null, "500.0", SettingApplyState.LIVE),
            new SettingDefinition("price.DESTROYED_STATION", SettingCategory.ECONOMY,
                    "Remove station", SettingValueType.DECIMAL, null, 0.0, null, "50000.0",
                    SettingApplyState.LIVE),
            new SettingDefinition("price.DESTROYED_SENSOR", SettingCategory.ECONOMY,
                    "Remove sensor", SettingValueType.DECIMAL, null, 0.0, null, "500.0",
                    SettingApplyState.LIVE),
            new SettingDefinition("price.DESTROYED_SEMAPHORE", SettingCategory.ECONOMY,
                    "Remove semaphore", SettingValueType.DECIMAL, null, 0.0, null, "500.0",
                    SettingApplyState.LIVE),
            new SettingDefinition("price.DESTROYED_LOCOMOTIVE", SettingCategory.ECONOMY,
                    "Scrap locomotive", SettingValueType.DECIMAL, null, 0.0, null, "30000.0",
                    SettingApplyState.LIVE),
            new SettingDefinition("price.DESTROYED_WAGON", SettingCategory.ECONOMY, "Scrap wagon",
                    SettingValueType.DECIMAL, null, 0.0, null, "10000.0", SettingApplyState.LIVE),
            new SettingDefinition("price.LOAD_PASSENGERS", SettingCategory.ECONOMY,
                    "Load passengers", SettingValueType.DECIMAL, null, 0.0, null, "1000.0",
                    SettingApplyState.LIVE),
            new SettingDefinition("price.UNLOAD_PASSENGERS", SettingCategory.ECONOMY,
                    "Unload passengers", SettingValueType.DECIMAL, null, 0.0, null, "1000.0",
                    SettingApplyState.LIVE),
            new SettingDefinition("price.TRAIN_MOVED", SettingCategory.ECONOMY, "Train moved",
                    SettingValueType.DECIMAL, null, 0.0, null, "0.0", SettingApplyState.LIVE),
            new SettingDefinition("price.TRAIN_CRASHED", SettingCategory.ECONOMY, "Crash fine",
                    SettingValueType.DECIMAL, null, 0.0, null, "1000000.0", SettingApplyState.LIVE),

            new SettingDefinition("delay.NORMAL_TRACK", SettingCategory.CONSTRUCTION,
                    "Normal track delay", SettingValueType.INTEGER, "ticks", 0.0, null, "0",
                    SettingApplyState.LIVE),
            new SettingDefinition("delay.BRIDGE_TRACK", SettingCategory.CONSTRUCTION,
                    "Bridge delay", SettingValueType.INTEGER, "ticks", 0.0, null, "20",
                    SettingApplyState.LIVE),
            new SettingDefinition("delay.BRIDGE_GATE_TRACK", SettingCategory.CONSTRUCTION,
                    "Bridge gate delay", SettingValueType.INTEGER, "ticks", 0.0, null, "20",
                    SettingApplyState.LIVE),
            new SettingDefinition("delay.TUNNEL_TRACK", SettingCategory.CONSTRUCTION,
                    "Tunnel delay", SettingValueType.INTEGER, "ticks", 0.0, null, "30",
                    SettingApplyState.LIVE),
            new SettingDefinition("delay.TUNNEL_GATE_TRACK", SettingCategory.CONSTRUCTION,
                    "Tunnel gate delay", SettingValueType.INTEGER, "ticks", 0.0, null, "30",
                    SettingApplyState.LIVE),
            new SettingDefinition("derail.minCurveInterval", SettingCategory.CONSTRUCTION,
                    "Minimum curve interval", SettingValueType.INTEGER, "ticks", 0.0, null, "12",
                    SettingApplyState.LIVE),
            new SettingDefinition("derail.minSpeed", SettingCategory.CONSTRUCTION,
                    "Minimum derail speed", SettingValueType.INTEGER, null, 0.0, null, "3",
                    SettingApplyState.LIVE),

            new SettingDefinition("ui.highlightBlockedTracks", SettingCategory.UI,
                    "Highlight blocked tracks", SettingValueType.BOOLEAN, null, null, null, "true",
                    SettingApplyState.LIVE));

    private static final Map<String, SettingDefinition> BY_KEY = indexByKey();

    private SettingsCatalog() {}

    /** All known settings, in form order. */
    public static List<SettingDefinition> definitions() {
        return DEFINITIONS;
    }

    /** Definition of {@code key}, or empty when the catalogue does not know it. */
    public static Optional<SettingDefinition> definition(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }

    /**
     * Default value of every known setting, in form order. The seed default is the literal
     * {@code random}: a new game draws a fresh one (D3).
     */
    public static Map<String, String> defaults() {
        Map<String, String> defaults = new LinkedHashMap<>();
        for (SettingDefinition def : DEFINITIONS) {
            defaults.put(def.key(), def.defaultValue());
        }
        return defaults;
    }

    /**
     * Validates one setting and returns its normalised text form.
     *
     * @throws IllegalArgumentException when the key is unknown, the value is missing or malformed,
     *         or it falls outside the definition's range
     */
    public static String validate(String key, String value) {
        SettingDefinition def = definition(key).orElseThrow(
                () -> new IllegalArgumentException("Unknown setting key: '" + key + "'"));
        if (value == null) {
            throw new IllegalArgumentException("Setting '" + key + "' needs a value");
        }
        String trimmed = value.trim();
        switch (def.type()) {
            case BOOLEAN:
                if (!trimmed.equalsIgnoreCase("true") && !trimmed.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException(
                            "Setting '" + key + "' must be true or false, got: '" + value + "'");
                }
                return trimmed.toLowerCase(Locale.ROOT);
            case SEED:
                if (trimmed.equalsIgnoreCase("random")) {
                    return "random";
                }
                return String.valueOf(parseInteger(def, trimmed));
            case INTEGER:
                return String.valueOf(parseInteger(def, trimmed));
            case DECIMAL:
                return parseDecimal(def, trimmed);
            default:
                throw new IllegalStateException("Unhandled setting type: " + def.type());
        }
    }

    /**
     * Validates a whole settings map (known keys only; unknown keys are ignored so a savegame from
     * a newer version still loads) and checks the cross-field rules: water + mountain ≤
     * {@value #MAX_TERRAIN_PERCENT} %. Returns the normalised values.
     *
     * @throws IllegalArgumentException on the first invalid value
     */
    public static Map<String, String> validateAll(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (definition(entry.getKey()).isPresent()) {
                normalized.put(entry.getKey(), validate(entry.getKey(), entry.getValue()));
            }
        }
        if (normalized.containsKey("terrain.waterPercent")
                && normalized.containsKey("terrain.mountainPercent")) {
            int water = Integer.parseInt(normalized.get("terrain.waterPercent"));
            int mountain = Integer.parseInt(normalized.get("terrain.mountainPercent"));
            if (water + mountain > MAX_TERRAIN_PERCENT) {
                throw new IllegalArgumentException("Water + mountain must be at most "
                        + MAX_TERRAIN_PERCENT + " %, got " + water + " + " + mountain);
            }
        }
        return normalized;
    }

    private static int parseInteger(SettingDefinition def, String value) {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Setting '" + def.key() + "' needs an integer, got: '" + value + "'", e);
        }
        if (def.min() != null && parsed < def.min()) {
            throw new IllegalArgumentException("Setting '" + def.key() + "' must be ≥ "
                    + def.min().intValue() + ", got " + parsed);
        }
        if (def.max() != null && parsed > def.max()) {
            throw new IllegalArgumentException("Setting '" + def.key() + "' must be ≤ "
                    + def.max().intValue() + ", got " + parsed);
        }
        return parsed;
    }

    private static String parseDecimal(SettingDefinition def, String value) {
        double parsed;
        try {
            parsed = Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Setting '" + def.key() + "' needs a number, got: '" + value + "'", e);
        }
        if (!Double.isFinite(parsed)) {
            throw new IllegalArgumentException(
                    "Setting '" + def.key() + "' needs a finite number, got: '" + value + "'");
        }
        if (def.min() != null && parsed < def.min()) {
            throw new IllegalArgumentException(
                    "Setting '" + def.key() + "' must be ≥ " + def.min() + ", got " + parsed);
        }
        if (def.max() != null && parsed > def.max()) {
            throw new IllegalArgumentException(
                    "Setting '" + def.key() + "' must be ≤ " + def.max() + ", got " + parsed);
        }
        return value.trim();
    }

    private static Map<String, SettingDefinition> indexByKey() {
        Map<String, SettingDefinition> byKey = new LinkedHashMap<>();
        for (SettingDefinition def : DEFINITIONS) {
            SettingDefinition previous = byKey.put(def.key(), def);
            if (previous != null) {
                throw new IllegalStateException("Duplicate setting key: " + def.key());
            }
        }
        return byKey;
    }
}
