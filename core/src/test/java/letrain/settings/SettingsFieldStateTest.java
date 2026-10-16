package letrain.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
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
    @DisplayName("a null lock and unknown scenario keys never change a known field's state")
    void should_IgnoreUnknownScenarioKeys() {
        assertEquals(SettingFieldState.LIVE,
                SettingsCatalog.fieldState(definition("startingBalance"), null),
                "a missing lock behaves like an empty one");
        assertEquals(SettingFieldState.NEW_GAME,
                SettingsCatalog.fieldState(definition("terrain.waterPercent"), null));
        assertEquals(SettingFieldState.LIVE,
                SettingsCatalog.fieldState(definition("startingBalance"),
                        Set.of("does.not.exist", "legacy.key")),
                "keys the catalogue does not know are simply not matches");
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
    @DisplayName("overDefaults fills an empty map and falls back for explicit null values")
    void should_OverlayDefaultsForEmptyAndNullEntries() {
        assertEquals(SettingsCatalog.defaults(), SettingsCatalog.overDefaults(Map.of()),
                "an empty configuration still yields every field");

        Map<String, String> partial = new HashMap<>();
        partial.put("startingBalance", null);
        partial.put("world.latitude", "50.0");
        Map<String, String> values = SettingsCatalog.overDefaults(partial);

        assertEquals("0.0", values.get("startingBalance"),
                "a null value falls back to the catalogue default");
        assertEquals("50.0", values.get("world.latitude"), "a present value is kept");
        assertEquals(SettingsCatalog.definitions().size(), values.size());
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

    @Test
    @DisplayName("changedReapplyKeys flags additions and changes, never removals, and mutates nothing")
    void should_DetectAddedChangedAndRemovedKeys() {
        Map<String, String> baseline = new LinkedHashMap<>();
        baseline.put("world.latitude", "40.0");
        Map<String, String> form = new LinkedHashMap<>();
        form.put("world.latitude", "50.0"); // change
        form.put("map.VIEW_RADIUS", "30"); // addition: the REAPPLY key was not in the baseline
        Map<String, String> baselineSnapshot = new LinkedHashMap<>(baseline);
        Map<String, String> formSnapshot = new LinkedHashMap<>(form);

        assertEquals(List.of("world.latitude", "map.VIEW_RADIUS"),
                SettingsCatalog.changedReapplyKeys(baseline, form),
                "both the changed and the added REAPPLY keys are flagged, in catalogue order");
        assertEquals(baselineSnapshot, baseline, "the baseline is never mutated");
        assertEquals(formSnapshot, form, "the form is never mutated");

        Map<String, String> withoutLatitude = new LinkedHashMap<>(baseline);
        withoutLatitude.remove("world.latitude");
        assertEquals(List.of(), SettingsCatalog.changedReapplyKeys(baseline, withoutLatitude),
                "a key missing from the form is not part of the payload and never confirms");
    }

    @Test
    @DisplayName("changedReapplyKeys tolerates a null baseline or form")
    void should_TolerateNullBaselineOrForm() {
        Map<String, String> form = Map.of("world.latitude", "50.0");

        assertEquals(List.of("world.latitude"), SettingsCatalog.changedReapplyKeys(null, form),
                "an absent baseline reads as empty text");
        assertEquals(List.of(),
                SettingsCatalog.changedReapplyKeys(SettingsCatalog.defaults(), null),
                "a null form has nothing to confirm");
    }

    private static SettingDefinition definition(String key) {
        return SettingsCatalog.definition(key).orElseThrow();
    }
}
