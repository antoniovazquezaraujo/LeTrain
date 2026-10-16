package letrain.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * S4b (#778): the field-state and draft helpers shared by the 2D terminal and the 3D HUD. Extracted
 * from the terminal's {@code SettingsDialog} so both clients read the same states, baselines and
 * REAPPLY confirmations.
 */
@DisplayName("Shared Settings catalogue helpers (#778)")
class SettingsFieldStateTest {

    @Test
    @DisplayName("the catalogue state maps to LIVE/REAPPLY/NEW_GAME and the scenario lock wins")
    void should_MapFieldState() {
        Set<String> none = Set.of();

        assertEquals(SettingFieldState.LIVE,
                SettingsCatalog.fieldState(definition("startingBalance"), none));
        assertEquals(SettingFieldState.REAPPLY,
                SettingsCatalog.fieldState(definition("world.latitude"), none));
        assertEquals(SettingFieldState.NEW_GAME,
                SettingsCatalog.fieldState(definition("terrain.waterPercent"), none));
        assertEquals(SettingFieldState.SCENARIO,
                SettingsCatalog.fieldState(definition("startingBalance"),
                        Set.of("startingBalance")),
                "a scenario-fixed key is locked even when the catalogue says LIVE");
    }

    @Test
    @DisplayName("only LIVE and REAPPLY are editable; NEW_GAME and SCENARIO are locked")
    void should_DecideEditability() {
        assertTrue(SettingFieldState.LIVE.editable());
        assertTrue(SettingFieldState.REAPPLY.editable());
        assertFalse(SettingFieldState.NEW_GAME.editable());
        assertFalse(SettingFieldState.SCENARIO.editable());
    }

    @Test
    @DisplayName("overDefaults keeps the effective values and fills the missing ones with defaults")
    void should_OverlayDefaults() {
        Map<String, String> values = SettingsCatalog.overDefaults(Map.of("startingBalance", "42"));

        assertEquals("42", values.get("startingBalance"), "the effective value is kept");
        assertEquals("1440", values.get("time.dayDurationSeconds"),
                "a missing key falls back to the catalogue default");
        assertEquals(SettingsCatalog.definitions().size(), values.size());
    }

    @Test
    @DisplayName("overDefaults also fills a null map with every default")
    void should_OverlayDefaultsWhenNull() {
        assertEquals(SettingsCatalog.defaults(), SettingsCatalog.overDefaults(null));
    }

    @Test
    @DisplayName("changedReapplyKeys only flags changed REAPPLY keys, ignoring whitespace")
    void should_DetectChangedReapplyKeys() {
        Map<String, String> baseline = new LinkedHashMap<>();
        baseline.put("world.latitude", "40.0");
        baseline.put("startingBalance", "0.0");

        assertEquals(List.of("world.latitude"), SettingsCatalog.changedReapplyKeys(baseline,
                Map.of("world.latitude", "50.0", "startingBalance", "100.0")));
        assertEquals(List.of(),
                SettingsCatalog.changedReapplyKeys(baseline, Map.of("world.latitude", " 40.0 ")),
                "whitespace and LIVE changes do not confirm");
    }

    private static SettingDefinition definition(String key) {
        return SettingsCatalog.definition(key).orElseThrow();
    }
}
