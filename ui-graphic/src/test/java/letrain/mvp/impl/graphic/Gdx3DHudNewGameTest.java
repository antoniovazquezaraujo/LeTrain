package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import letrain.mvp.NewGameText;
import letrain.settings.NewGameForm;
import letrain.settings.SettingCategory;
import letrain.settings.SettingDefinition;
import letrain.settings.SettingsCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * S4 (#808): the pure pieces of the 3D New Game dialog. The 3D dialog mirrors the 2D one, so the
 * page model, the tab mnemonics and the Ctrl+Tab ring are functions of the shared catalogue and of
 * the shared Settings helpers; Start validates through {@link NewGameForm#toSettings()} exactly
 * like the presenter-driven 2D dialog. All of it is pinned here without a GL context.
 *
 * <p>
 * The widget wiring itself (building {@code Table}/{@code TextField}/{@code CheckBox} actors, the
 * Tab cycle, the inline message panel) needs libGDX's Skin/Stage and is covered by the presenter
 * tests instead.
 */
@DisplayName("3D New Game dialog helpers (#808)")
class Gdx3DHudNewGameTest {

    @Test
    @DisplayName("the dialog offers the five catalogue pages in the shared order")
    void should_OfferTheFivePagesInTheSharedOrder() {
        List<SettingCategory> pages = Gdx3DHud.settingsCategories();

        assertEquals(List.of(SettingCategory.TERRAIN, SettingCategory.WORLD,
                SettingCategory.ECONOMY, SettingCategory.CONSTRUCTION, SettingCategory.UI), pages);
        assertEquals(5, pages.size(), "Terrain, Game, Economy, Build and UI");
    }

    @Test
    @DisplayName("each non-Terrain page is the catalogue fields of its category, in catalogue order")
    void should_GroupEachPagesFieldsFromTheCatalogue() {
        List<SettingDefinition> all = SettingsCatalog.definitions();
        List<SettingDefinition> grouped = new ArrayList<>();

        for (SettingCategory category : Gdx3DHud.settingsCategories()) {
            List<SettingDefinition> fields = Gdx3DHud.categoryDefinitions(category);

            assertTrue(fields.stream().allMatch(d -> d.category() == category),
                    category + " must only carry its own fields: " + fields);
            grouped.addAll(fields);

            if (category != SettingCategory.TERRAIN) {
                assertFalse(fields.isEmpty(),
                        category + " is an editable column, it must not be empty");
            }
        }

        assertEquals(5, Gdx3DHud.settingsCategories().size());
        assertEquals(all, grouped,
                "the five pages partition the catalogue without dropping or reordering a field");
    }

    @Test
    @DisplayName("the hand-built Terrain block owns exactly the three terrain catalogue keys")
    void should_KeepTheTerrainPageHandBuilt() {
        // The Terrain page shows the live block (seed + the two sliders); the other four pages are
        // the catalogue columns, so the terrain keys never leak into them.
        List<String> terrainKeys = Gdx3DHud.categoryDefinitions(SettingCategory.TERRAIN).stream()
                .map(SettingDefinition::key).toList();

        assertEquals(List.of("seed", "terrain.waterPercent", "terrain.mountainPercent"),
                terrainKeys);
        for (SettingCategory category : Gdx3DHud.settingsCategories()) {
            if (category == SettingCategory.TERRAIN) {
                continue;
            }
            assertTrue(
                    Gdx3DHud.categoryDefinitions(category).stream()
                            .noneMatch(d -> d.category() == SettingCategory.TERRAIN),
                    category + " must not repeat a terrain field");
        }
    }

    @Test
    @DisplayName("every tab label mnemonics its shared letter (T/G/E/B/U), case-insensitively")
    void should_MnemonicTheTabsWithTheSharedLetters() {
        Map<SettingCategory, String> expected = Map.of(SettingCategory.TERRAIN, "[GREEN]T[]errain",
                SettingCategory.WORLD, "[GREEN]G[]ame", SettingCategory.ECONOMY, "[GREEN]E[]conomy",
                SettingCategory.CONSTRUCTION, "[GREEN]B[]uild", SettingCategory.UI, "[GREEN]U[]I");

        for (SettingCategory category : Gdx3DHud.settingsCategories()) {
            char letter = Gdx3DHud.settingsTabLetter(category);
            String label = Gdx3DHud.settingsHotkeyLabel(category.displayName(), letter);

            assertEquals(expected.get(category), label, category + " tab mnemonic");
            assertEquals(category, Gdx3DHud.settingsTabHotkey(letter).orElseThrow(),
                    "the painted letter must reach the same page");
        }
    }

    @Test
    @DisplayName("Alt+letter jumps to a page; an unrelated letter selects nothing")
    void should_JumpToAPageWithAltLetter() {
        assertEquals(SettingCategory.TERRAIN, Gdx3DHud.settingsTabHotkey('t').orElseThrow());
        assertEquals(SettingCategory.WORLD, Gdx3DHud.settingsTabHotkey('G').orElseThrow());
        assertEquals(SettingCategory.ECONOMY, Gdx3DHud.settingsTabHotkey('e').orElseThrow());
        assertEquals(SettingCategory.CONSTRUCTION, Gdx3DHud.settingsTabHotkey('b').orElseThrow());
        assertEquals(SettingCategory.UI, Gdx3DHud.settingsTabHotkey('U').orElseThrow());

        assertTrue(Gdx3DHud.settingsTabHotkey('x').isEmpty());
        assertTrue(Gdx3DHud.settingsTabHotkey('1').isEmpty());
    }

    @Test
    @DisplayName("Ctrl+Tab cycles the pages forward and backward and wraps around")
    void should_CycleThePagesWithCtrlTab() {
        assertEquals(SettingCategory.WORLD,
                Gdx3DHud.settingsCycleTab(SettingCategory.TERRAIN, false));
        assertEquals(SettingCategory.UI, Gdx3DHud.settingsCycleTab(SettingCategory.TERRAIN, true),
                "reverse wraps");
        assertEquals(SettingCategory.TERRAIN, Gdx3DHud.settingsCycleTab(SettingCategory.UI, false),
                "forward wraps");

        // A full forward lap over the five pages lands back on the start.
        SettingCategory cursor = SettingCategory.ECONOMY;
        for (int i = 0; i < Gdx3DHud.settingsCategories().size(); i++) {
            cursor = Gdx3DHud.settingsCycleTab(cursor, false);
        }
        assertEquals(SettingCategory.ECONOMY, cursor);
    }

    @Test
    @DisplayName("S and C start/cancel: they are actions, never tab hotkeys")
    void should_KeepStartAndCancelOutOfTheTabHotkeys() {
        assertTrue(Gdx3DHud.settingsTabHotkey('S').isEmpty(), "S starts the game");
        assertTrue(Gdx3DHud.settingsTabHotkey('c').isEmpty(), "C cancels the dialog");
        assertEquals("[GREEN]S[]tart", Gdx3DHud.settingsHotkeyLabel(NewGameText.START, 'S'));
        assertEquals("[GREEN]C[]ancel", Gdx3DHud.settingsHotkeyLabel(NewGameText.CANCEL, 'C'));
    }

    @Test
    @DisplayName("Start rejects an invalid catalogue value with the catalogue message")
    void should_RejectAnInvalidCatalogueValueOnStart() {
        NewGameForm form = NewGameForm.forNewGame(Map.of(), new Random(1));
        form.setValue("startingBalance", "lots");

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, form::toSettings);

        assertTrue(error.getMessage().contains("startingBalance"),
                "the player reads which field failed: " + error.getMessage());
        assertEquals("Invalid setting", NewGameText.INVALID_TITLE,
                "the error channel is titled with the shared NewGameText.INVALID_TITLE");
    }

    @Test
    @DisplayName("Start hands the validated settings map (terrain + catalogue) to onStart")
    void should_HandTheValidatedSettingsToStart() {
        NewGameForm form = NewGameForm.forNewGame(Map.of(), new Random(1));
        form.setWaterPercent(30);
        form.setMountainPercent(5);

        Map<String, String> settings = form.toSettings();

        assertEquals("30", settings.get(NewGameForm.WATER_KEY));
        assertEquals("5", settings.get(NewGameForm.MOUNTAIN_KEY));
        assertEquals(String.valueOf(form.seed()), settings.get(NewGameForm.SEED_KEY));
        assertEquals("true", settings.get("ui.highlightBlockedTracks"));
        assertEquals("40.0", settings.get("world.latitude"));
    }

    @Test
    @DisplayName("#804: the slider repeat reuses the game input's 400 ms / 50 ms timings")
    void should_ReuseTheInputAutoRepeatTimings() {
        // The repeat itself is a scene2d Action driven by the frame clock (needs a Stage), but the
        // timings are single-sourced from the input handler so the dialog and the game feel alike.
        assertEquals(400L, Gdx3DInputHandler.INITIAL_REPEAT_DELAY_MS,
                "initial delay before the slider bursts");
        assertEquals(50L, Gdx3DInputHandler.REPEAT_INTERVAL_MS, "steps per repeat burst");
    }

    @Test
    @DisplayName("a non-Terrain edit stays live in the form after leaving the page")
    void should_KeepANonTerrainEditLiveInTheForm() {
        NewGameForm form = NewGameForm.forNewGame(Map.of(), new Random(1));

        // The UI checkbox and an Economy text field are bound live to the form (#808).
        form.setValue("ui.highlightBlockedTracks", "false");
        form.setValue("startingBalance", "777");

        Map<String, String> settings = form.toSettings();

        assertEquals("false", settings.get("ui.highlightBlockedTracks"));
        assertEquals(777.0, Double.parseDouble(settings.get("startingBalance")), 1e-9);
    }
}
