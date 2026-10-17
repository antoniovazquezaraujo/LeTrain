package letrain.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import letrain.economy.impl.EconomyManager;
import letrain.mvp.impl.EventLogManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Settings catalogue: metadata and validation")
class SettingsCatalogTest {

    private static SettingApplyState applyStateOf(String key) {
        return SettingsCatalog.definition(key).orElseThrow().applyState();
    }

    @Test
    @DisplayName("the catalogue covers every v1 field of the concept (terrain, world, economy, construction, UI)")
    void catalogue_coversV1Fields() {
        List<String> keys =
                SettingsCatalog.definitions().stream().map(SettingDefinition::key).toList();

        List<String> expected = List.of("seed", "terrain.waterPercent", "terrain.mountainPercent",
                "world.latitude", "time.dayDurationSeconds", "time.startDay", "time.startTime",
                "map.VIEW_RADIUS", "startingBalance", "fuelCostPerMeter", "cargoLoadingFee",
                "cargo.GOLD", "cargo.COAL", "cargo.RUBY", "price.CONSTRUCTED_NORMAL_RAIL_TRACK",
                "price.DESTROYED_NORMAL_RAIL_TRACK", "price.TRAIN_CRASHED", "delay.NORMAL_TRACK",
                "delay.BRIDGE_TRACK", "delay.TUNNEL_TRACK", "derail.minCurveInterval",
                "derail.minSpeed", "ui.highlightBlockedTracks");
        assertTrue(keys.containsAll(expected),
                "missing keys: " + expected.stream().filter(k -> !keys.contains(k)).toList());

        for (SettingCategory category : SettingCategory.values()) {
            assertTrue(SettingsCatalog.definitions().stream()
                    .anyMatch(def -> def.category() == category), "empty category: " + category);
        }
    }

    @Test
    @DisplayName("defaults match the engine's effective defaults")
    void defaults_matchEngineDefaults() {
        EconomyManager manager = new EconomyManager(new EventLogManager());
        Map<String, String> effective = manager.effectiveConfig();

        for (SettingDefinition def : SettingsCatalog.definitions()) {
            if (def.type() == SettingValueType.SEED) {
                continue; // the seed is not an economy setting
            }
            assertTrue(effective.containsKey(def.key()), "not in effectiveConfig: " + def.key());
            String actual = effective.get(def.key());
            switch (def.type()) {
                case INTEGER:
                    assertEquals(Integer.parseInt(def.defaultValue()), Integer.parseInt(actual),
                            def.key());
                    break;
                case DECIMAL:
                    assertEquals(Double.parseDouble(def.defaultValue()), Double.parseDouble(actual),
                            1e-9, def.key());
                    break;
                case BOOLEAN:
                    assertEquals(Boolean.parseBoolean(def.defaultValue()),
                            Boolean.parseBoolean(actual), def.key());
                    break;
                default:
                    break;
            }
        }
    }

    @Test
    @DisplayName("terrain percentages default to the D2 decision (25 % water / 10 % mountain)")
    void terrainDefaults_areD2() {
        assertEquals("25",
                SettingsCatalog.definition("terrain.waterPercent").orElseThrow().defaultValue());
        assertEquals("10",
                SettingsCatalog.definition("terrain.mountainPercent").orElseThrow().defaultValue());
        assertEquals(25, SettingsCatalog.DEFAULT_WATER_PERCENT);
        assertEquals(10, SettingsCatalog.DEFAULT_MOUNTAIN_PERCENT);
    }

    @Test
    @DisplayName("the S2b start keys default to the ADR-022 origin (day 1, 08:00)")
    void startDefaults_areD1At0800() {
        SettingDefinition startDay = SettingsCatalog.definition("time.startDay").orElseThrow();
        SettingDefinition startTime = SettingsCatalog.definition("time.startTime").orElseThrow();

        assertEquals(SettingCategory.WORLD, startDay.category());
        assertEquals(SettingValueType.INTEGER, startDay.type());
        assertEquals("1", startDay.defaultValue());
        assertEquals(1.0, startDay.min());
        assertEquals(366.0, startDay.max());
        assertEquals(SettingCategory.WORLD, startTime.category());
        assertEquals(SettingValueType.TIME_OF_DAY, startTime.type());
        assertEquals("08:00", startTime.defaultValue());
    }

    @Test
    @DisplayName("apply states follow the concept: terrain NEW_GAME, world REAPPLY, the rest LIVE")
    void applyStates_matchConcept() {
        assertEquals(SettingApplyState.NEW_GAME, applyStateOf("seed"));
        assertEquals(SettingApplyState.NEW_GAME, applyStateOf("terrain.waterPercent"));
        assertEquals(SettingApplyState.NEW_GAME, applyStateOf("terrain.mountainPercent"));
        assertEquals(SettingApplyState.NEW_GAME, applyStateOf("time.startDay"));
        assertEquals(SettingApplyState.NEW_GAME, applyStateOf("time.startTime"));
        assertEquals(SettingApplyState.REAPPLY, applyStateOf("world.latitude"));
        assertEquals(SettingApplyState.REAPPLY, applyStateOf("map.VIEW_RADIUS"));
        assertEquals(SettingApplyState.LIVE, applyStateOf("time.dayDurationSeconds"));
        assertEquals(SettingApplyState.LIVE, applyStateOf("startingBalance"));
        assertEquals(SettingApplyState.LIVE, applyStateOf("fuelCostPerMeter"));
        assertEquals(SettingApplyState.LIVE, applyStateOf("cargo.GOLD"));
        assertEquals(SettingApplyState.LIVE, applyStateOf("price.TRAIN_CRASHED"));
        assertEquals(SettingApplyState.LIVE, applyStateOf("delay.TUNNEL_TRACK"));
        assertEquals(SettingApplyState.LIVE, applyStateOf("derail.minSpeed"));
        assertEquals(SettingApplyState.LIVE, applyStateOf("ui.highlightBlockedTracks"));
    }

    @ParameterizedTest
    @CsvSource({"terrain.waterPercent, -1", "terrain.waterPercent, 96",
            "terrain.mountainPercent, 96", "world.latitude, -91", "world.latitude, 91",
            "time.dayDurationSeconds, 59", "time.dayDurationSeconds, 86401", "map.VIEW_RADIUS, 4",
            "time.startDay, 0", "time.startDay, 367", "time.startTime, 24:00",
            "time.startTime, 12:60", "time.startTime, 12:5", "time.startTime, noon",
            "startingBalance, -0.01", "fuelCostPerMeter, -1", "delay.TUNNEL_TRACK, -1",
            "derail.minSpeed, -1", "derail.minCurveInterval, -2",
            "ui.highlightBlockedTracks, maybe", "seed, notANumber", "unknown.key, 1"})
    @DisplayName("validation rejects out-of-range, malformed and unknown values")
    void validate_rejectsInvalid(String key, String value) {
        assertThrows(IllegalArgumentException.class, () -> SettingsCatalog.validate(key, value));
    }

    @Test
    @DisplayName("validation normalises valid values")
    void validate_normalisesValidValues() {
        assertEquals("25", SettingsCatalog.validate("terrain.waterPercent", " 25 "));
        assertEquals("95", SettingsCatalog.validate("terrain.waterPercent", "95"));
        assertEquals("0", SettingsCatalog.validate("terrain.mountainPercent", "0"));
        assertEquals("random", SettingsCatalog.validate("seed", "RANDOM"));
        assertEquals("123", SettingsCatalog.validate("seed", "123"));
        assertEquals("-5", SettingsCatalog.validate("seed", "-5"));
        assertEquals("false", SettingsCatalog.validate("ui.highlightBlockedTracks", "FALSE"));
        assertEquals("true", SettingsCatalog.validate("ui.highlightBlockedTracks", "true"));
        assertEquals("40.0", SettingsCatalog.validate("world.latitude", "40.0"));
        assertEquals("0.5", SettingsCatalog.validate("fuelCostPerMeter", "0.5"));
        assertEquals("1", SettingsCatalog.validate("time.startDay", " 1 "));
        assertEquals("366", SettingsCatalog.validate("time.startDay", "366"));
        assertEquals("06:30", SettingsCatalog.validate("time.startTime", "6:30"),
                "H:MM is accepted and zero-padded");
        assertEquals("08:00", SettingsCatalog.validate("time.startTime", "8:00"));
        assertEquals("00:00", SettingsCatalog.validate("time.startTime", " 00:00 "));
        assertEquals("23:59", SettingsCatalog.validate("time.startTime", "23:59"));
    }

    @Test
    @DisplayName("cross-field rule: water + mountain must leave at least 5 % of field")
    void validateAll_enforcesCrossFieldRule() {
        Map<String, String> tooMuch = new LinkedHashMap<>();
        tooMuch.put("terrain.waterPercent", "60");
        tooMuch.put("terrain.mountainPercent", "40");
        assertThrows(IllegalArgumentException.class, () -> SettingsCatalog.validateAll(tooMuch));

        Map<String, String> exactlyFull = new LinkedHashMap<>();
        exactlyFull.put("terrain.waterPercent", "60");
        exactlyFull.put("terrain.mountainPercent", "35");
        assertEquals(exactlyFull, SettingsCatalog.validateAll(exactlyFull),
                "60 + 35 = 95 is valid");

        Map<String, String> single = new LinkedHashMap<>();
        single.put("terrain.waterPercent", "95");
        assertEquals(single, SettingsCatalog.validateAll(single), "a single tail is always valid");
    }

    @Test
    @DisplayName("validateAll ignores unknown keys so newer savegames still load")
    void validateAll_ignoresUnknownKeys() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("terrain.waterPercent", "30");
        values.put("some.future.key", "whatever");

        Map<String, String> normalized = SettingsCatalog.validateAll(values);

        assertFalse(normalized.containsKey("some.future.key"));
        assertEquals("30", normalized.get("terrain.waterPercent"));
    }

    @Test
    @DisplayName("defaults() exposes every definition in form order")
    void defaults_coversEveryDefinition() {
        Map<String, String> defaults = SettingsCatalog.defaults();
        for (SettingDefinition def : SettingsCatalog.definitions()) {
            assertEquals(def.defaultValue(), defaults.get(def.key()), def.key());
        }
        assertEquals("random", defaults.get("seed"));
        assertEquals(SettingsCatalog.definitions().size(), defaults.size());
    }

    @Test
    @DisplayName("scenarioConfiguration() = every catalogue key but seed, in form order (#782)")
    void scenarioConfiguration_coversCatalogueExceptSeed() {
        Map<String, String> configuration = SettingsCatalog.scenarioConfiguration(null);

        List<String> expected = SettingsCatalog.definitions().stream().map(SettingDefinition::key)
                .filter(key -> !"seed".equals(key)).toList();
        assertEquals(expected, List.copyOf(configuration.keySet()), "form order, no seed");
        assertFalse(configuration.containsKey("seed"));
        for (SettingDefinition def : SettingsCatalog.definitions()) {
            if (!"seed".equals(def.key())) {
                assertEquals(def.defaultValue(), configuration.get(def.key()), def.key());
            }
        }
    }

    @Test
    @DisplayName("scenarioConfiguration() lets effective values win, falls back to defaults (#782)")
    void scenarioConfiguration_effectiveWinsAndDropsUnknownKeys() {
        Map<String, String> effective = new LinkedHashMap<>();
        effective.put("terrain.waterPercent", "40");
        effective.put("threshold.WATER", "130"); // legacy key: not in the catalogue

        Map<String, String> configuration = SettingsCatalog.scenarioConfiguration(effective);

        assertEquals("40", configuration.get("terrain.waterPercent"), "effective value wins");
        assertEquals("10", configuration.get("terrain.mountainPercent"), "missing key -> default");
        assertFalse(configuration.containsKey("threshold.WATER"),
                "keys outside the catalogue are dropped");
    }

    @Test
    @DisplayName("scenarioConfiguration() drops the seed even when the effective map carries it (#782)")
    void scenarioConfiguration_dropsSeedFromEffective() {
        Map<String, String> effective = new LinkedHashMap<>();
        effective.put("seed", "12345");
        effective.put("terrain.waterPercent", "40");

        Map<String, String> configuration = SettingsCatalog.scenarioConfiguration(effective);

        assertFalse(configuration.containsKey("seed"),
                "the scenario writes its own seed line; the block must not repeat it");
        assertEquals("40", configuration.get("terrain.waterPercent"));
    }

    @Test
    @DisplayName("scenarioConfiguration() keeps the form order regardless of the effective map order (#782)")
    void scenarioConfiguration_keepsFormOrder() {
        Map<String, String> effective = new LinkedHashMap<>();
        effective.put("ui.highlightBlockedTracks", "false");
        effective.put("terrain.waterPercent", "40");
        effective.put("startingBalance", "10.0");

        Map<String, String> configuration = SettingsCatalog.scenarioConfiguration(effective);

        List<String> expected = SettingsCatalog.definitions().stream().map(SettingDefinition::key)
                .filter(key -> !"seed".equals(key)).toList();
        assertEquals(expected, List.copyOf(configuration.keySet()));
        assertEquals("false", configuration.get("ui.highlightBlockedTracks"));
        assertEquals("40", configuration.get("terrain.waterPercent"));
        assertEquals("10.0", configuration.get("startingBalance"));
    }

    @Test
    @DisplayName("scenarioConfiguration() passes values through verbatim without validating (#782)")
    void scenarioConfiguration_doesNotValidateValues() {
        Map<String, String> effective = new LinkedHashMap<>();
        effective.put("terrain.waterPercent", "not-a-number");

        Map<String, String> configuration = SettingsCatalog.scenarioConfiguration(effective);

        assertEquals("not-a-number", configuration.get("terrain.waterPercent"),
                "generation is a formatting step; the apply path rejects malformed values later");
    }
}
