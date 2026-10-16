package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import letrain.mvp.SettingsText;
import letrain.palette.VisualPalette;
import letrain.settings.SettingCategory;
import letrain.settings.SettingDefinition;
import letrain.settings.SettingFieldState;
import letrain.settings.SettingsCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * S4b (#778): the pure pieces of the 3D in-game Settings dialog. The category order, the field
 * states (scenario lock wins, mirror of 2D), the labels, the glyph colours, the legend and the
 * widget collection are functions of the catalogue, so they are pinned here without a GL context.
 */
@DisplayName("3D Settings dialog helpers (#778)")
class Gdx3DHudSettingsTest {

    @Test
    @DisplayName("the five catalogue categories show in the shared order")
    void should_ListTheCategories() {
        assertEquals(
                List.of(SettingCategory.TERRAIN, SettingCategory.WORLD, SettingCategory.ECONOMY,
                        SettingCategory.CONSTRUCTION, SettingCategory.UI),
                Gdx3DHud.settingsCategories());
    }

    @Test
    @DisplayName("the 3D field states mirror 2D: LIVE/REAPPLY editable, scenario lock wins")
    void should_MapFieldStates() {
        assertEquals(SettingFieldState.NEW_GAME,
                Gdx3DHud.settingsFieldState(definition("seed"), Set.of()));
        assertEquals(SettingFieldState.LIVE,
                Gdx3DHud.settingsFieldState(definition("startingBalance"), Set.of()));
        assertEquals(SettingFieldState.REAPPLY,
                Gdx3DHud.settingsFieldState(definition("world.latitude"), Set.of()));
        assertEquals(SettingFieldState.SCENARIO, Gdx3DHud
                .settingsFieldState(definition("startingBalance"), Set.of("startingBalance")));

        assertTrue(Gdx3DHud.settingsFieldState(definition("startingBalance"), Set.of()).editable());
        assertFalse(Gdx3DHud.settingsFieldState(definition("seed"), Set.of()).editable());
    }

    @Test
    @DisplayName("the label shows the display name plus its unit, when the catalogue has one")
    void should_LabelFieldsWithTheirUnit() {
        assertEquals("Water (%)", Gdx3DHud.settingsFieldLabel(definition("terrain.waterPercent")));
        assertEquals("View radius (cells)",
                Gdx3DHud.settingsFieldLabel(definition("map.VIEW_RADIUS")));
        assertEquals("Starting balance", Gdx3DHud.settingsFieldLabel(definition("startingBalance")),
                "a unit-less field keeps its bare name");
    }

    @Test
    @DisplayName("every state gets a distinct glyph colour, matching the 2D palette")
    void should_ColourTheGlyphs() {
        assertEquals(Color.GREEN, Gdx3DHud.settingsGlyphColor(SettingFieldState.LIVE));
        assertEquals(Color.YELLOW, Gdx3DHud.settingsGlyphColor(SettingFieldState.REAPPLY));
        assertEquals(Color.GRAY, Gdx3DHud.settingsGlyphColor(SettingFieldState.SCENARIO));
        Color newGame = Gdx3DHud.settingsGlyphColor(SettingFieldState.NEW_GAME);
        assertNotNull(newGame);
        assertTrue(newGame.r > newGame.g && newGame.r > newGame.b, "NEW_GAME reads red");
    }

    @Test
    @DisplayName("the legend lists the three shared entries and no scenario colour (#767)")
    void should_RenderTheLegend() {
        String legend = Gdx3DHud.settingsLegendMarkup();

        assertTrue(legend.contains(SettingsText.LEGEND_LIVE), legend);
        assertTrue(legend.contains(SettingsText.LEGEND_REAPPLY), legend);
        assertTrue(legend.contains(SettingsText.LEGEND_NEW_GAME), legend);
        assertEquals(3, legend.split(Gdx3DHud.SETTINGS_GLYPH, -1).length - 1,
                "exactly one glyph per documented state, no scenario entry: " + legend);
    }

    @Test
    @DisplayName("editableValues reads only the LIVE/REAPPLY fields of the form")
    void should_CollectEditableValues() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("startingBalance", "10");
        form.put("seed", "7");
        form.put("world.latitude", "41.5");

        assertEquals(Map.of("startingBalance", "10", "world.latitude", "41.5"),
                Gdx3DHud.editableValues(form, Set.of()),
                "NEW_GAME fields are left out of the Apply payload");
        assertEquals(Map.of("startingBalance", "10"),
                Gdx3DHud.editableValues(form, Set.of("world.latitude")),
                "a scenario-locked key is not applied either");
    }

    @Test
    @DisplayName("editableValues ignores unknown keys, never injects absent ones and does not mutate")
    void should_NotMutateOrInventEditableValues() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("startingBalance", "10");
        form.put("totally.unknown.key", "x");
        Map<String, String> snapshot = new LinkedHashMap<>(form);

        Map<String, String> values = Gdx3DHud.editableValues(form, Set.of());

        assertEquals(Map.of("startingBalance", "10"), values,
                "a key outside the catalogue never becomes editable");
        assertFalse(values.containsKey("time.dayDurationSeconds"),
                "fields absent from the form are not invented");
        assertEquals(snapshot, form, "the working form is left untouched");
    }

    @Test
    @DisplayName("the terrain bar always adds up to the requested width, field is the remainder")
    void should_StackTheTerrainBarToTheFullWidth() {
        // Draw order: water, field, mountain (mirrors the 2D NewGameDialog cases).
        assertArrayEquals(new int[] {10, 26, 4}, Gdx3DHud.stackedBarWidths(25, 10, 40));
        assertArrayEquals(new int[] {0, 40, 0}, Gdx3DHud.stackedBarWidths(0, 0, 40));
        assertArrayEquals(new int[] {38, 2, 0}, Gdx3DHud.stackedBarWidths(95, 0, 40));
        // Largest remainder keeps every proportion visible: 33/34/33 over 10 cells.
        assertArrayEquals(new int[] {3, 4, 3}, Gdx3DHud.stackedBarWidths(33, 33, 10));
    }

    @Test
    @DisplayName("the terrain bar segments always total the width and keep a zero segment empty")
    void should_SumTheTerrainBarSegmentsAndKeepZeroEmpty() {
        int width = Gdx3DHud.TERRAIN_BAR_WIDTH;
        int[][] cases = {{25, 10}, {0, 0}, {0, 100}, {50, 0}, {95, 0}, {100, 0}, {33, 33}};
        for (int[] percent : cases) {
            int[] cells = Gdx3DHud.stackedBarWidths(percent[0], percent[1], width);
            assertEquals(3, cells.length, "water/field/mountain");
            assertEquals(width, cells[0] + cells[1] + cells[2],
                    "water " + percent[0] + " mountain " + percent[1] + " must fill " + width);
        }
        // A zero proportion must claim no cells when the bar still has room elsewhere.
        assertArrayEquals(new int[] {0, width, 0}, Gdx3DHud.stackedBarWidths(0, 0, width));
    }

    @Test
    @DisplayName("terrainDayColor is opaque and keeps the exact day RGB tones (#780)")
    void should_ReturnOpaqueDayTones() {
        // rgb888ToColor leaves the alpha of a fresh Color at 0; without the guard the Terrain bar
        // and preview would be 100 % transparent.
        assertTone(VisualPalette.Token.TERRAIN_WATER, 0x3366CC);
        assertTone(VisualPalette.Token.TERRAIN_FIELDS, 0x66994C);
        assertTone(VisualPalette.Token.TERRAIN_MOUNTAIN, 0x80664D);
    }

    @Test
    @DisplayName("the tab letter is the first letter of the section name (T/G/E/B/U)")
    void should_MapTabLetters() {
        assertEquals('T', Gdx3DHud.settingsTabLetter(SettingCategory.TERRAIN));
        assertEquals('G', Gdx3DHud.settingsTabLetter(SettingCategory.WORLD));
        assertEquals('E', Gdx3DHud.settingsTabLetter(SettingCategory.ECONOMY));
        assertEquals('B', Gdx3DHud.settingsTabLetter(SettingCategory.CONSTRUCTION));
        assertEquals('U', Gdx3DHud.settingsTabLetter(SettingCategory.UI));
    }

    @Test
    @DisplayName("Alt+letter selects the tab case-insensitively, empty for any other key (#780)")
    void should_ResolveTabHotkeys() {
        assertEquals(SettingCategory.TERRAIN, Gdx3DHud.settingsTabHotkey('T').orElseThrow());
        assertEquals(SettingCategory.TERRAIN, Gdx3DHud.settingsTabHotkey('t').orElseThrow());
        assertEquals(SettingCategory.WORLD, Gdx3DHud.settingsTabHotkey('G').orElseThrow());
        assertEquals(SettingCategory.ECONOMY, Gdx3DHud.settingsTabHotkey('e').orElseThrow());
        assertEquals(SettingCategory.CONSTRUCTION, Gdx3DHud.settingsTabHotkey('B').orElseThrow());
        assertEquals(SettingCategory.UI, Gdx3DHud.settingsTabHotkey('u').orElseThrow());

        assertTrue(Gdx3DHud.settingsTabHotkey('A').isEmpty(), "an action letter is not a tab");
        assertTrue(Gdx3DHud.settingsTabHotkey('X').isEmpty());
    }

    @Test
    @DisplayName("the mnemonic label paints the letter green like the 2D mnemonicRenderer (#780)")
    void should_FormatMnemonicLabels() {
        assertEquals("[GREEN]A[]pply", Gdx3DHud.settingsHotkeyLabel(SettingsText.APPLY, 'A'));
        assertEquals("[GREEN]N[]ew game", Gdx3DHud.settingsHotkeyLabel(SettingsText.NEW_GAME, 'N'));
        assertEquals("[GREEN]S[]ave as defaults",
                Gdx3DHud.settingsHotkeyLabel(SettingsText.SAVE_DEFAULTS, 'S'));
        assertEquals("plain", Gdx3DHud.settingsHotkeyLabel("plain", 'Z'),
                "a label without the mnemonic is returned unchanged");
    }

    @Test
    @DisplayName("the five footer actions carry the 2D Alt mnemonics A/R/N/S/C (#780)")
    void should_MapActionHotkeys() {
        assertEquals("apply,restore,newGame,saveDefaults,close", Gdx3DHud.SETTINGS_ACTIONS.stream()
                .map(Gdx3DHud.SettingsAction::id).reduce((a, b) -> a + "," + b).orElseThrow());
        assertEquals("ARNSC", Gdx3DHud.SETTINGS_ACTIONS.stream()
                .map(a -> String.valueOf(a.mnemonic())).reduce("", String::concat));
        assertEquals(SettingsText.NEW_GAME, Gdx3DHud.SETTINGS_ACTIONS.get(2).label());
    }

    @Test
    @DisplayName("Ctrl+Tab cycles the categories forward and Ctrl+Shift+Tab backwards (#780)")
    void should_CycleTabs() {
        assertEquals(SettingCategory.WORLD,
                Gdx3DHud.settingsCycleTab(SettingCategory.TERRAIN, false));
        assertEquals(SettingCategory.TERRAIN, Gdx3DHud.settingsCycleTab(SettingCategory.UI, false),
                "wraps forward");
        assertEquals(SettingCategory.UI, Gdx3DHud.settingsCycleTab(SettingCategory.TERRAIN, true),
                "wraps backwards");
    }

    @Test
    @DisplayName("Ctrl+Tab walks the full ring and a round trip lands back on the start (#780)")
    void should_RoundTripTheTabCycle() {
        for (SettingCategory start : Gdx3DHud.settingsCategories()) {
            SettingCategory forward = Gdx3DHud.settingsCycleTab(start, false);
            assertEquals(start, Gdx3DHud.settingsCycleTab(forward, true),
                    "forward then backward must return to " + start);
        }
        // Five forward steps from any category close the ring (one already pinned above).
        SettingCategory cursor = SettingCategory.ECONOMY;
        for (int i = 0; i < Gdx3DHud.settingsCategories().size(); i++) {
            cursor = Gdx3DHud.settingsCycleTab(cursor, false);
        }
        assertEquals(SettingCategory.ECONOMY, cursor, "a full forward lap returns to the start");
    }

    @Test
    @DisplayName("an unknown active category cycles to the first tab, never crashes (#780)")
    void should_FallBackToTheFirstTabForAnUnknownCategory() {
        assertEquals(SettingCategory.TERRAIN, Gdx3DHud.settingsCycleTab(null, false));
        assertEquals(SettingCategory.TERRAIN, Gdx3DHud.settingsCycleTab(null, true));
    }

    @Test
    @DisplayName("the tab hotkey only matches its five letters, any other key is empty (#780)")
    void should_IgnoreNonTabHotkeys() {
        assertEquals(SettingCategory.TERRAIN, Gdx3DHud.settingsTabHotkey('T').orElseThrow());
        // The action letters never select a tab.
        for (char letter : new char[] {'A', 'R', 'N', 'S', 'C', 'X', '1', ' ', '-', 0}) {
            assertTrue(Gdx3DHud.settingsTabHotkey(letter).isEmpty(),
                    "'" + letter + "' must not resolve to a tab");
        }
    }

    @Test
    @DisplayName("revealInScroll only maps actors inside the scroll pane (guard, #780)")
    void should_OnlyRevealActorsInsideTheScrollPane() {
        // Arrange: a scroll pane stand-in with a nested field, and a sibling footer actor.
        Group scrollPane = new Group();
        Group fieldsTable = new Group();
        Actor nestedField = new Actor();
        Actor footerButton = new Actor();
        fieldsTable.addActor(nestedField);
        scrollPane.addActor(fieldsTable);

        // Act/Assert: nested actors are revealable, anything else is skipped.
        assertTrue(Gdx3DHud.isInsideScroll(nestedField, scrollPane),
                "a nested field lives inside the pane");
        assertTrue(Gdx3DHud.isInsideScroll(fieldsTable, scrollPane),
                "a direct child is inside the pane");
        assertFalse(Gdx3DHud.isInsideScroll(footerButton, scrollPane),
                "a footer button is NOT a descendant: localToAscendantCoordinates would throw");
        assertTrue(Gdx3DHud.isInsideScroll(scrollPane, scrollPane),
                "libGDX counts an actor as its own descendant (harmless: maps to 0,0)");
        assertFalse(Gdx3DHud.isInsideScroll(null, scrollPane));
        assertFalse(Gdx3DHud.isInsideScroll(nestedField, null));
    }

    @Test
    @DisplayName("revealScrollY keeps a visible rect put and aligns an off-screen one (#780)")
    void should_ComputeTheRevealScroll() {
        // Content 100 tall, 20-tall viewport. A rect inside the window keeps the scroll.
        assertEquals(30f, Gdx3DHud.revealScrollY(55f, 10f, 100f, 20f, 30f),
                "55..65 sits in the 50..70 window of scrollY=30");
        // A rect above the window pulls the scroll so its top meets the window top.
        assertEquals(45f, Gdx3DHud.revealScrollY(50f, 5f, 100f, 20f, 60f),
                "50..55 is above the 20..40 window of scrollY=60: top aligns at 55");
        // A rect below the window pushes the scroll so its bottom meets the window bottom.
        assertEquals(50f, Gdx3DHud.revealScrollY(30f, 5f, 100f, 20f, 10f),
                "30..35 is below the 70..90 window of scrollY=10: bottom aligns at 30");
    }

    private static void assertTone(VisualPalette.Token token, int rgb) {
        Color color = Gdx3DHud.terrainDayColor(token);
        assertEquals(1f, color.a, "the alpha guard must lift rgb888ToColor's a=0");
        assertEquals(((rgb >> 16) & 0xFF) / 255f, color.r, 1e-6f,
                "red of 0x" + Integer.toHexString(rgb));
        assertEquals(((rgb >> 8) & 0xFF) / 255f, color.g, 1e-6f,
                "green of 0x" + Integer.toHexString(rgb));
        assertEquals((rgb & 0xFF) / 255f, color.b, 1e-6f, "blue of 0x" + Integer.toHexString(rgb));
    }

    private static SettingDefinition definition(String key) {
        return SettingsCatalog.definition(key).orElseThrow();
    }
}
