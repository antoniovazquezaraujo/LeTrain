package letrain.mvp.impl.terminal;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.DefaultMutableThemeStyle;
import com.googlecode.lanterna.graphics.DelegatingTheme;
import com.googlecode.lanterna.graphics.DelegatingThemeDefinition;
import com.googlecode.lanterna.graphics.Theme;
import com.googlecode.lanterna.graphics.ThemeDefinition;
import com.googlecode.lanterna.graphics.ThemeStyle;
import com.googlecode.lanterna.gui2.AbstractComponent;
import com.googlecode.lanterna.gui2.AbstractInteractableComponent;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.CheckBox;
import com.googlecode.lanterna.gui2.Component;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.Interactable;
import com.googlecode.lanterna.gui2.InteractableRenderer;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LayoutManager;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowListenerAdapter;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import letrain.ground.TerrainPreview;
import letrain.mvp.NewGameText;
import letrain.settings.NewGameForm;
import letrain.settings.SettingCategory;
import letrain.settings.SettingDefinition;
import letrain.settings.SettingValueType;
import letrain.settings.SettingsCatalog;
import letrain.visitor.terminal.TerminalPalette;

/**
 * 2D New Game dialog (S2, issue #746; layout polished for #749, field format #751). Full-screen,
 * borderless, built from the shared catalogue:
 *
 * <ul>
 * <li><b>Terrain</b> tab (opens first): seed (random, editable and copyable), water/mountain
 * sliders honouring the catalogue rule (water + mountain ≤ 95 %, the field is the remainder), a 100
 * % stacked bar that spans the water/mountain row, and a left-aligned adaptive preview that samples
 * {@link TerrainPreview} with the same calibrated thresholds the world generator uses (#755 removed
 * the target/measured line). The terrain controls live only in this tab: they are no longer pinned
 * above every section (#749).</li>
 * <li><b>Sections</b>: Game, Economy, Build and UI read {@link SettingsCatalog#definitions()} as a
 * single scrolling column of {@code Name: value} rows (the 3-field grid felt cramped, #749; no
 * range hints, #751; each page aligns its values after its own widest name and keeps one empty
 * column on the left, #755/#757).</li>
 * <li><b>Start</b> validates the whole form and returns the settings map to the presenter, which
 * builds the world via {@code Model.newGame}. <b>Esc</b> or <b>Alt+C</b> cancels (the Cancel button
 * regained its mnemonic when Construction became Build, #757). <b>Alt+T/G/E/B/U</b> switch tabs,
 * <b>Ctrl+Tab</b>/<b>Ctrl+Shift+Tab</b> cycle them and <b>Alt+S</b> starts; the tab bar is out of
 * the focus cycle, so <b>Tab</b> walks the active page's fields plus Start/Cancel only (#757;
 * letter hotkeys #755; the seed is drawn once at open, #754).</li>
 * </ul>
 *
 * <p>
 * The layout is a pure function of the terminal size ({@link #previewPlan(int, int)} and
 * {@link #revealOffset(int, int, int, int, int)}), so the adaptive behaviour is testable without a
 * terminal. The window runs on a black-background theme (#757) and text fields replace their value
 * on the first keystroke after taking focus (#757).
 */
final class NewGameDialog {

    /** Minimum terminal for any preview. */
    static final int MIN_COLUMNS = 80;
    static final int MIN_ROWS = 24;

    /** Rows the tab bar takes: always visible on top, every section is swapped below it. */
    static final int TAB_ROWS = 1;
    /** Rows the Start/Cancel footer takes. */
    static final int FOOTER_ROWS = 1;
    /** Blank row between the tab bar and the first field of every section (#754). */
    static final int SECTION_TOP_ROWS = 1;
    /** Empty column on the left of every page body, so the content floats a bit (#757). */
    static final int LEFT_MARGIN_COLUMNS = 1;
    /**
     * Rows the terrain controls above the preview take inside the Terrain tab: seed, water,
     * mountain, the 100 % bar and the blank row before the map (the target/measured line is gone,
     * #755; the map gap is #757).
     */
    static final int TERRAIN_CHROME_ROWS = 5;

    /** Rows one tooltip box takes when a commit is rejected: top border, text, bottom border. */
    static final int TOOLTIP_ROWS = 3;

    /** Width of one slider's bar, in cells. */
    static final int SLIDER_BAR_WIDTH = 40;
    /** Width of the seed text box, in cells. */
    static final int SEED_BOX_WIDTH = 10;

    /** Wide enough for the longest default ({@code 1000000.0}) plus the caret. */
    private static final int FIELD_WIDTH = 12;
    /** Margin the preview keeps against the section edges, in cells. */
    private static final int PREVIEW_MARGIN_COLUMNS = 4;

    /**
     * Game-palette day colours of the preview and the stacked bar (the tones of the calibration
     * demo): sea blue, 2D board green and the shared mountain brown. The old preview painted the
     * mountain with ANSI white (#749).
     */
    static final int WATER_RGB = 0x3366CC;
    static final int FIELD_RGB = 0x4CA331;
    static final int MOUNTAIN_RGB = 0x80664D;

    /** Preview sizes: {@code sampleRows} is the sampled grid height; each text row shows two. */
    record PreviewPlan(int columns, int sampleRows) {
        int textRows() {
            return (sampleRows + 1) / 2;
        }
    }

    /** Rows left for one section body: terminal minus the tab bar, the blank row and the footer. */
    static int sectionRows(int rows) {
        return rows - TAB_ROWS - FOOTER_ROWS - SECTION_TOP_ROWS;
    }

    /**
     * How much preview fits in a terminal of {@code columns x rows}: {@code null} below 80x24
     * (concept §7). The sample grid is square and covers the whole calibration window, so the
     * half-block render (two sample rows per text row, each column a full cell wide) shows the
     * world with its 1:1 cells; a screen-wide grid stretched the world horizontally (#749). The
     * preview is half as wide and half as tall as the first design — about a quarter of the section
     * area — so it no longer takes over the dialog (#754).
     */
    static PreviewPlan previewPlan(int columns, int rows) {
        if (columns < MIN_COLUMNS || rows < MIN_ROWS) {
            return null;
        }
        int previewTextRows = sectionRows(rows) - TERRAIN_CHROME_ROWS;
        if (previewTextRows < 2) {
            return null;
        }
        int sample = Math.min((columns - PREVIEW_MARGIN_COLUMNS) / 2, (previewTextRows / 2) * 2);
        return new PreviewPlan(sample, sample);
    }

    /**
     * Width of a slider bar when the value column starts at {@code valueColumn}: the full
     * {@link #SLIDER_BAR_WIDTH} when it fits, down to 5 cells in narrow terminals. The closing
     * {@code "] 100%"} span takes 7 cells after the bar.
     */
    static int sliderBarWidth(int columns, int valueColumn) {
        return Math.min(SLIDER_BAR_WIDTH, Math.max(5, columns - valueColumn - 7));
    }

    /**
     * Width of the 100 % stacked bar: the full Water/Mountain row, from the left edge to the end of
     * the slider percentage ({@code valueColumn + bar + 7}; 59 cells at 70 columns, #755).
     */
    static int stackedBarWidth(int columns, int valueColumn) {
        return Math.min(columns, valueColumn + sliderBarWidth(columns, valueColumn) + 7);
    }

    /**
     * Segment widths {@code [water, field, mountain]} of the 100 % stacked bar (draw order), so
     * every segment totals exactly {@code width} cells (largest remainder: no segment vanishes
     * while it has cells to claim).
     */
    static int[] stackedBarWidths(int waterPercent, int mountainPercent, int width) {
        int[] parts =
                {waterPercent, Math.max(0, 100 - waterPercent - mountainPercent), mountainPercent};
        int[] cells = new int[3];
        int[] remainders = new int[3];
        int used = 0;
        for (int i = 0; i < 3; i++) {
            cells[i] = parts[i] * width / 100;
            remainders[i] = parts[i] * width % 100;
            used += cells[i];
        }
        while (used < width) {
            int best = 0;
            for (int i = 1; i < 3; i++) {
                if (remainders[i] > remainders[best]) {
                    best = i;
                }
            }
            cells[best]++;
            remainders[best] = -1;
            used++;
        }
        return cells;
    }

    /** Label of one catalogue field: only the display name, no range hints (#751). */
    static String fieldLabel(SettingDefinition definition) {
        return definition.displayName();
    }

    /**
     * Column (0-based) where the values of one page start: after its widest name plus the colon and
     * one space (#755). Each page computes its own column, so short pages (Terrain: 12, Seed and
     * sliders sit close) lose the big gap the old global column left on the left of the values.
     */
    static int valueColumn(List<String> names) {
        return names.stream().mapToInt(String::length).max().orElse(0) + 2;
    }

    /** Display names of the fields of one catalogue section, in form order. */
    static List<String> fieldNames(SettingCategory category) {
        return SettingsCatalog.definitions().stream()
                .filter(definition -> definition.category() == category)
                .map(SettingDefinition::displayName).toList();
    }

    /**
     * {@code Name:} padded with spaces so the value starts at {@code column}; the widest name keeps
     * exactly one space after the colon (#751, per-page columns #755).
     */
    static String alignedLabel(String name, int column) {
        return name + ":" + " ".repeat(Math.max(1, column - name.length() - 1));
    }

    /** Unit shown after the value; {@code "ticks"} in full, the single column has room for it. */
    static String fieldUnit(SettingDefinition definition) {
        return definition.unitValue().orElse("");
    }

    /** Allowed-range hint of the tooltip shown when a commit is rejected (#751). */
    static String allowedRange(SettingDefinition definition) {
        if (definition.type() == SettingValueType.TIME_OF_DAY) {
            return "Allowed: 00:00..23:59";
        }
        if (definition.min() != null && definition.max() != null) {
            return "Allowed: " + number(definition.min()) + ".." + number(definition.max());
        }
        if (definition.min() != null) {
            return "Minimum: " + number(definition.min());
        }
        if (definition.max() != null) {
            return "Maximum: " + number(definition.max());
        }
        return "Allowed: whole number";
    }

    /**
     * Normalised text of a candidate commit, or {@code null} when it is not a valid value for the
     * field: out of range, malformed, or a seed that is not a whole number (the seed's literal
     * {@code random} is resolved when the dialog opens, so the box only takes integers).
     */
    static String normalizeCommit(SettingDefinition definition, String text) {
        try {
            if (definition.type() == SettingValueType.SEED) {
                return String.valueOf(Integer.parseInt(text.trim()));
            }
            return SettingsCatalog.validate(definition.key(), text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /**
     * Terminal colour of a terrain value for the preview and the stacked bar. The RGB tones are the
     * game palette's day keys, resolved for the detected depth. In 16 colours the nearest RGB slot
     * to brown is a neutral grey (the mountain used to read as stone/white, #749), so rock falls
     * back to the same dark red the 2D map degrades its rock background to: never white.
     */
    static TextColor terrainColor(TerminalPalette palette, int terrain) {
        switch (terrain) {
            case TerrainPreview.WATER:
                return palette.colorOf(WATER_RGB);
            case TerrainPreview.ROCK:
                if (palette.depth() == TerminalPalette.Depth.ANSI_16) {
                    return TextColor.ANSI.RED;
                }
                return palette.colorOf(MOUNTAIN_RGB);
            default:
                return palette.colorOf(FIELD_RGB);
        }
    }

    /**
     * Vertical offset (index of the first visible row) that keeps rows {@code [top, bottom)}
     * visible in a viewport of {@code viewport} rows, moving as little as possible and staying in
     * bounds. Pure maths so the scrolling column is tested without opening a terminal.
     */
    static int revealOffset(int offset, int top, int bottom, int viewport, int totalRows) {
        int maxOffset = Math.max(0, totalRows - viewport);
        int candidate = offset;
        if (top < offset) {
            candidate = top;
        } else if (bottom > offset + viewport) {
            candidate = bottom - viewport;
        }
        return Math.max(0, Math.min(maxOffset, candidate));
    }

    /** Letter of the Alt shortcut of a tab: the first letter of its name ({@code T/G/E/B/U}). */
    static char tabLetter(SettingCategory category) {
        return Character.toUpperCase(category.displayName().charAt(0));
    }

    /**
     * Tab selected by an Alt access key: its letter ({@code T/G/E/B/U}, case-insensitive), empty
     * for any other key (#755).
     */
    static Optional<SettingCategory> tabHotkey(char key, List<SettingCategory> tabs) {
        char letter = Character.toLowerCase(key);
        for (SettingCategory category : tabs) {
            if (letter == Character.toLowerCase(tabLetter(category))) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }

    /**
     * Tab that follows (or precedes, when {@code reverse}) {@code active} in the tab order,
     * wrapping around. Pure maths for the Ctrl+Tab / Ctrl+Shift+Tab cycle (#757).
     */
    static SettingCategory cycleTab(SettingCategory active, List<SettingCategory> tabs,
            boolean reverse) {
        int index = tabs.indexOf(active);
        if (index < 0) {
            return tabs.get(0);
        }
        int step = reverse ? -1 : 1;
        return tabs.get(Math.floorMod(index + step, tabs.size()));
    }

    /**
     * Opens the modal dialog and returns the validated settings, or {@code null} when the player
     * cancels (Esc/Cancel).
     */
    static Map<String, String> show(Screen screen, Map<String, String> currentSettings) {
        NewGameForm form = NewGameForm.forNewGame(currentSettings);
        MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);
        gui.setTheme(darkTheme(gui.getTheme()));
        TerminalPalette palette = TerminalPalette.detect();
        BasicWindow window = new BasicWindow();
        window.setHints(List.of(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS));
        window.setCloseWindowWithEscape(true);

        final Map<String, String>[] result = new Map[] {null};
        final Map<String, Supplier<String>> readers = new LinkedHashMap<>();

        Panel root = new Panel(new BorderLayout());
        Panel content = new Panel(new BorderLayout());

        // --- Terrain tab: seed, sliders, bar and preview (only here since #749) -----
        final TerrainControls terrainControls = new TerrainControls(form.seed(),
                form.waterPercent(), form.mountainPercent(), palette, screen.getTerminalSize());
        final int[] seed = {form.seed()};

        Runnable recalibrate = () -> terrainControls.setValues(seed[0], form.waterPercent(),
                form.mountainPercent());

        Runnable onSliderChange = () -> {
            int newWater = terrainControls.water().value();
            int newMountain = terrainControls.mountain().value();
            form.setWaterPercent(newWater);
            form.setMountainPercent(newMountain);
            terrainControls.water().setMaxAndValue(
                    SettingsCatalog.MAX_TERRAIN_PERCENT - form.mountainPercent(),
                    form.waterPercent());
            terrainControls.mountain().setMaxAndValue(
                    SettingsCatalog.MAX_TERRAIN_PERCENT - form.waterPercent(),
                    form.mountainPercent());
            recalibrate.run();
            window.invalidate();
        };
        terrainControls.water().setOnChange(onSliderChange);
        terrainControls.mountain().setOnChange(onSliderChange);
        terrainControls.seedBox().setTextChangeListener((text, ignored) -> {
            // Fired by typing: keep the preview on the last valid seed.
            try {
                seed[0] = Integer.parseInt(text.trim());
                recalibrate.run();
                window.invalidate();
            } catch (NumberFormatException e) {
                // Keep the last valid seed for the preview; Start validates the field.
            }
        });

        ScrollColumn terrain = new ScrollColumn();
        terrainControls.addTo(terrain);

        // --- Sections ----------------------------------------------------------------------
        final List<SettingCategory> tabs = List.of(SettingCategory.TERRAIN, SettingCategory.WORLD,
                SettingCategory.ECONOMY, SettingCategory.CONSTRUCTION, SettingCategory.UI);
        Map<SettingCategory, Panel> sections = new EnumMap<>(SettingCategory.class);
        sections.put(SettingCategory.TERRAIN, terrain);
        for (SettingCategory category : tabs) {
            if (category != SettingCategory.TERRAIN) {
                sections.put(category, buildSection(category, form, readers));
            }
        }

        Panel tabBar = new Panel(new LinearLayout(Direction.HORIZONTAL));
        final SettingCategory[] active = {SettingCategory.TERRAIN};
        for (SettingCategory category : tabs) {
            // Tab buttons stay out of the focus cycle (#757): Alt+letter and Ctrl+Tab reach them.
            Button button = new Button(category.displayName()) {
                @Override
                public boolean isFocusable() {
                    return false;
                }
            };
            button.setRenderer(tabRenderer(category, active));
            button.addListener(triggered -> {
                active[0] = category;
                showSection(content, sections, category, window);
            });
            tabBar.addComponent(button);
        }

        // --- Footer and window -------------------------------------------------------------
        Runnable startAction = () -> {
            try {
                form.setSeed(terrainControls.seedBox().getText());
                for (Map.Entry<String, Supplier<String>> entry : readers.entrySet()) {
                    form.setValue(entry.getKey(), entry.getValue().get());
                }
                form.setWaterPercent(terrainControls.water().value());
                form.setMountainPercent(terrainControls.mountain().value());
                result[0] = form.toSettings();
                window.close();
            } catch (IllegalArgumentException e) {
                new MessageDialogBuilder().setTitle(NewGameText.INVALID_TITLE)
                        .setText(String.valueOf(e.getMessage())).addButton(MessageDialogButton.OK)
                        .build().showDialog(gui);
            }
        };

        Panel footer = new Panel(new LinearLayout(Direction.HORIZONTAL));
        footer.addComponent(mnemonicButton(NewGameText.START, 'S', startAction));
        // Build freed the C: Cancel gets its mnemonic (and Alt+C) back (#757).
        footer.addComponent(mnemonicButton(NewGameText.CANCEL, 'C', window::close));

        // Ctrl+Tab cycles the tabs and Alt+letter jumps to one; Start keeps Alt+S and Cancel
        // Alt+C (Esc works too). Handled here, before the focused widget sees the stroke (#757).
        window.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onInput(Window w, KeyStroke keyStroke, AtomicBoolean deliverEvent) {
                if (keyStroke.isCtrlDown() && (keyStroke.getKeyType() == KeyType.Tab
                        || keyStroke.getKeyType() == KeyType.ReverseTab)) {
                    active[0] =
                            cycleTab(active[0], tabs, keyStroke.getKeyType() == KeyType.ReverseTab);
                    showSection(content, sections, active[0], window);
                    deliverEvent.set(false);
                    return;
                }
                if (!keyStroke.isAltDown() || keyStroke.getCharacter() == null) {
                    return;
                }
                char key = Character.toLowerCase(keyStroke.getCharacter());
                Optional<SettingCategory> tab = tabHotkey(key, tabs);
                if (tab.isPresent()) {
                    active[0] = tab.get();
                    showSection(content, sections, active[0], window);
                    deliverEvent.set(false);
                } else if (key == 's') {
                    startAction.run();
                    deliverEvent.set(false);
                } else if (key == 'c') {
                    window.close();
                    deliverEvent.set(false);
                }
            }
        });

        root.addComponent(tabBar, BorderLayout.Location.TOP);
        root.addComponent(content, BorderLayout.Location.CENTER);
        root.addComponent(footer, BorderLayout.Location.BOTTOM);

        window.setComponent(root);
        showSection(content, sections, active[0], window); // sets the initial focus on the page
        recalibrate.run();
        gui.addWindowAndWait(window);
        return result[0];
    }



    /**
     * Swaps the active section into the content area; the tab bar repaints its active style. Every
     * section keeps a blank row under the tabs (#754), one empty column on the left (#757) and,
     * once the window is up, hands the focus to its first field (the previous page is gone).
     */
    private static void showSection(Panel content, Map<SettingCategory, Panel> sections,
            SettingCategory active, BasicWindow window) {
        content.removeAllComponents();
        content.addComponent(new EmptySpace(new TerminalSize(1, SECTION_TOP_ROWS)),
                BorderLayout.Location.TOP);
        content.addComponent(new EmptySpace(new TerminalSize(LEFT_MARGIN_COLUMNS, 1)),
                BorderLayout.Location.LEFT);
        Panel section = sections.get(active);
        content.addComponent(section, BorderLayout.Location.CENTER);
        if (window.getComponent() != null) {
            window.setFocusedInteractable(section.nextFocus(null));
        }
        window.invalidate();
    }

    /**
     * Horizontal row layout without Lanterna's default 1-column gap, so the value really lands on
     * the page's value column right after the padded {@code Name:} label (#757). The footer and the
     * tab bar keep their gap.
     */
    static LinearLayout rowLayout() {
        return new LinearLayout(Direction.HORIZONTAL).setSpacing(0);
    }

    /** Wraps a 1-row component so a BorderLayout stretches it to the available width. */
    private static Panel singleRowComponent(Component component) {
        Panel panel = new Panel(new BorderLayout());
        panel.addComponent(component, BorderLayout.Location.CENTER);
        return panel;
    }

    /**
     * The terrain controls shared by New Game and the in-game Settings dialog (#767): seed row,
     * water/mountain sliders, the 100 % stacked bar and the adaptive preview (or its no-preview
     * notice). New Game wires the callbacks; Settings adds the same block read-only with the
     * running world's values.
     */
    static final class TerrainControls {

        private final Panel seedHost;
        private final ValidatingTextBox seedBox;
        private final PercentSlider water;
        private final PercentSlider mountain;
        private final StackedBar bar;
        private final Panel barRow;
        private final EmptySpace gap;
        private final TerrainPreviewPanel previewPanel;
        private final Label noPreview;
        private final PreviewPlan plan;

        TerrainControls(int seed, int waterPercent, int mountainPercent, TerminalPalette palette,
                TerminalSize screen) {
            int column = valueColumn(List.of(NewGameText.SEED_LABEL, NewGameText.WATER_LABEL,
                    NewGameText.MOUNTAIN_LABEL));
            seedHost = new Panel(new LinearLayout(Direction.VERTICAL));
            Panel seedRow = new Panel(rowLayout());
            seedRow.addComponent(new Label(alignedLabel(NewGameText.SEED_LABEL, column)));
            seedBox = new ValidatingTextBox(
                    SettingsCatalog.definition(NewGameForm.SEED_KEY).orElseThrow(),
                    String.valueOf(seed), column);
            seedBox.setPreferredSize(new TerminalSize(SEED_BOX_WIDTH, 1));
            seedBox.setTooltipHost(seedHost);
            seedRow.addComponent(seedBox);
            seedHost.addComponent(seedRow);

            water = new PercentSlider(NewGameText.WATER_LABEL, column, waterPercent, () -> {
            });
            mountain =
                    new PercentSlider(NewGameText.MOUNTAIN_LABEL, column, mountainPercent, () -> {
                    });
            bar = new StackedBar(palette, column, waterPercent, mountainPercent);
            barRow = singleRowComponent(bar);
            gap = new EmptySpace(new TerminalSize(1, 1));
            previewPanel = new TerrainPreviewPanel(palette);
            noPreview = new Label(NewGameText.NO_PREVIEW);
            plan = previewPlan(screen.getColumns(), screen.getRows());
            setValues(seed, waterPercent, mountainPercent);
        }

        /** Adds the block rows, in order, to a section column: seed, water, mountain, bar, map. */
        void addTo(ScrollColumn column) {
            column.addComponent(seedHost);
            column.addComponent(water);
            column.addComponent(mountain);
            column.addComponent(barRow);
            // Blank line between the components and the map (or its notice, #757).
            column.addComponent(gap);
            column.addComponent(plan != null ? previewPanel : noPreview);
        }

        /** Repaints the bar and the preview (same noise/thresholds as the world generator). */
        void setValues(int seed, int waterPercent, int mountainPercent) {
            bar.setPercentages(waterPercent, mountainPercent);
            if (plan != null) {
                previewPanel.setGrid(TerrainPreview.sample(seed, waterPercent, mountainPercent,
                        plan.columns(), plan.sampleRows()));
            }
        }

        /** Read-only mode for the Settings dialog: same look, out of the focus cycle (#767). */
        void setEnabled(boolean enabled) {
            seedBox.setEnabled(enabled);
            water.setEnabled(enabled);
            mountain.setEnabled(enabled);
        }

        ValidatingTextBox seedBox() {
            return seedBox;
        }

        PercentSlider water() {
            return water;
        }

        PercentSlider mountain() {
            return mountain;
        }
    }

    /** Button with its Alt mnemonic highlighted, the same style as the editor footer. */
    static Button mnemonicButton(String label, char mnemonic, Runnable action) {
        Button button = new Button(label, action);
        button.setRenderer(TerminalView.mnemonicRenderer(mnemonic));
        return button;
    }

    /**
     * `< Terrain >` tabs: the active one is reversed, and the Alt mnemonic letter lives inside the
     * name (its first letter) highlighted like the footer buttons (#757).
     */
    static InteractableRenderer<Button> tabRenderer(SettingCategory category,
            SettingCategory[] active) {
        return new InteractableRenderer<Button>() {
            @Override
            public TerminalSize getPreferredSize(Button component) {
                return new TerminalSize(component.getLabel().length() + 4, 1);
            }

            @Override
            public void drawComponent(TextGUIGraphics graphics, Button component) {
                boolean isActive = active[0] == category;
                if (isActive) {
                    graphics.setForegroundColor(TextColor.ANSI.BLACK);
                    graphics.setBackgroundColor(TextColor.ANSI.CYAN);
                } else if (component.isFocused()) {
                    graphics.applyThemeStyle(component.getThemeDefinition().getActive());
                } else {
                    graphics.applyThemeStyle(component.getThemeDefinition().getNormal());
                }
                String label = component.getLabel();
                graphics.putString(0, 0, "< " + label + " >");
                if (isActive) {
                    graphics.enableModifiers(com.googlecode.lanterna.SGR.BOLD);
                } else {
                    graphics.setForegroundColor(TerminalView.MNEMONIC_COLOR);
                }
                // The mnemonic is the first letter, already inside the name: repaint it on top.
                graphics.putString(2, 0, label.substring(0, 1));
                graphics.disableModifiers(com.googlecode.lanterna.SGR.BOLD);
            }

            @Override
            public TerminalPosition getCursorLocation(Button component) {
                return null;
            }
        };
    }

    /**
     * Builds the single-column body of one catalogue section: one field per row, {@code Name:} plus
     * the value (+ unit) aligned on the page's own column (#755). The text of every widget is
     * registered in {@code readers} so Start collects the form in one place.
     */
    private static ScrollColumn buildSection(SettingCategory category, NewGameForm form,
            Map<String, Supplier<String>> readers) {
        ScrollColumn columnPanel = new ScrollColumn();
        int column = valueColumn(fieldNames(category));
        for (SettingDefinition definition : SettingsCatalog.definitions()) {
            if (definition.category() == category) {
                columnPanel.addComponent(fieldRow(definition, form, readers, column));
            }
        }
        return columnPanel;
    }

    /**
     * One field row ({@code Name:} + value + unit), wrapped so a rejected commit can open its
     * tooltip below the input line without moving the values (#751).
     */
    private static Panel fieldRow(SettingDefinition definition, NewGameForm form,
            Map<String, Supplier<String>> readers, int column) {
        Panel row = new Panel(new LinearLayout(Direction.VERTICAL));
        Panel line = new Panel(rowLayout());
        line.addComponent(new Label(alignedLabel(definition.displayName(), column)));
        if (definition.type() == SettingValueType.BOOLEAN) {
            CheckBox checkBox =
                    scrollingCheckBox(Boolean.parseBoolean(form.value(definition.key())));
            line.addComponent(checkBox);
            readers.put(definition.key(), () -> checkBox.isChecked() ? "true" : "false");
        } else {
            ValidatingTextBox textBox =
                    new ValidatingTextBox(definition, form.value(definition.key()), column);
            line.addComponent(textBox);
            String unit = fieldUnit(definition);
            if (!unit.isEmpty()) {
                line.addComponent(new Label(" " + unit));
            }
            readers.put(definition.key(), textBox::getText);
            textBox.setTooltipHost(row);
        }
        row.addComponent(line);
        return row;
    }

    /**
     * Text box that commits with Enter/Tab: a valid value updates the field, an invalid one is
     * discarded (the previous value comes back, the focus stays) and a tooltip with the allowed
     * range opens next to the field (#751). It also scrolls its column when it takes focus
     * (Tab/arrow form navigation) and emulates select-on-focus (#757): the first printable
     * character (or Backspace) after entering the field replaces the whole value; caret movements
     * cancel that "selection" without erasing anything.
     */
    static final class ValidatingTextBox extends TextBox {

        private final java.util.function.Function<String, String> normalizer;
        private final Supplier<String> tooltipText;
        private final int valueColumn;
        private String lastValid;
        private boolean armed;
        private Panel tooltipHost;
        private Panel tooltipLine;

        ValidatingTextBox(SettingDefinition definition, String value, int valueColumn) {
            this(value, valueColumn, text -> normalizeCommit(definition, text),
                    () -> allowedRange(definition));
        }

        /**
         * Text box with a custom validator (S3b live clock fields, #769): the normalizer returns
         * the accepted text or {@code null}, and the supplier names the allowed value in the
         * tooltip.
         */
        ValidatingTextBox(String value, int valueColumn,
                java.util.function.Function<String, String> normalizer,
                Supplier<String> tooltipText) {
            super(new TerminalSize(FIELD_WIDTH, 1), value);
            this.normalizer = normalizer;
            this.tooltipText = tooltipText;
            this.valueColumn = valueColumn;
            this.lastValid = value;
        }

        /** Panel that receives the tooltip line when a commit is rejected (the field row). */
        void setTooltipHost(Panel host) {
            this.tooltipHost = host;
        }

        @Override
        protected void afterEnterFocus(Interactable.FocusChangeDirection direction,
                Interactable previouslyInFocus) {
            armed = true; // the value reads as selected: the next keystroke replaces it (#757)
            revealInColumn(this);
        }

        @Override
        public Result handleKeyStroke(KeyStroke keyStroke) {
            KeyType keyType = keyStroke.getKeyType();
            if (armed && !keyStroke.isCtrlDown() && !keyStroke.isAltDown()) {
                if (keyType == KeyType.Character && keyStroke.getCharacter() != null
                        && !Character.isISOControl(keyStroke.getCharacter())) {
                    setText(String.valueOf(keyStroke.getCharacter()));
                    setCaretPosition(getText().length());
                    armed = false;
                    return Result.HANDLED;
                }
                if (keyType == KeyType.Backspace) {
                    setText("");
                    armed = false;
                    return Result.HANDLED;
                }
                if (keyType == KeyType.ArrowLeft || keyType == KeyType.ArrowRight
                        || keyType == KeyType.ArrowUp || keyType == KeyType.ArrowDown
                        || keyType == KeyType.Home || keyType == KeyType.End
                        || keyType == KeyType.PageUp || keyType == KeyType.PageDown
                        || keyType == KeyType.MouseEvent) {
                    armed = false; // moving the caret cancels the replacement, nothing is erased
                }
            }
            if (keyType == KeyType.Enter || keyType == KeyType.Tab
                    || keyType == KeyType.ReverseTab) {
                if (!commit()) {
                    return Result.HANDLED; // rejected: the focus stays on the field
                }
            }
            return super.handleKeyStroke(keyStroke);
        }

        /**
         * Applies the current text when valid and updates the previous value; otherwise reverts the
         * text and opens the range tooltip.
         *
         * @return {@code true} when the value was accepted
         */
        boolean commit() {
            String normalized = normalizer.apply(getText());
            if (normalized != null) {
                lastValid = normalized;
                if (!normalized.equals(getText())) {
                    setText(normalized);
                }
                hideTooltip();
                return true;
            }
            setText(lastValid);
            showTooltip();
            return false;
        }

        private void showTooltip() {
            if (tooltipHost == null) {
                return;
            }
            if (tooltipLine == null) {
                tooltipLine = new Panel(rowLayout());
                tooltipLine.addComponent(new EmptySpace(new TerminalSize(valueColumn, 1)));
                tooltipLine.addComponent(new TooltipBox(tooltipText.get()));
                tooltipHost.addComponent(tooltipLine);
            }
            tooltipHost.invalidate();
            revealInColumn(this);
        }

        private void hideTooltip() {
            if (tooltipLine != null && tooltipHost != null) {
                tooltipHost.removeComponent(tooltipLine);
                tooltipLine = null;
                tooltipHost.invalidate();
            }
        }
    }

    /** Bordered hint (three rows: top, text, bottom) opened next to a field (#751). */
    static final class TooltipBox extends AbstractComponent<TooltipBox> {

        private final String text;

        TooltipBox(String text) {
            this.text = text;
        }

        String text() {
            return text;
        }

        @Override
        protected ComponentRenderer<TooltipBox> createDefaultRenderer() {
            return new Renderer();
        }

        private final class Renderer implements ComponentRenderer<TooltipBox> {
            @Override
            public TerminalSize getPreferredSize(TooltipBox component) {
                return new TerminalSize(component.text.length() + 4, TOOLTIP_ROWS);
            }

            @Override
            public void drawComponent(TextGUIGraphics graphics, TooltipBox component) {
                graphics.setForegroundColor(TerminalView.NORMAL_MENU_FG_COLOR);
                graphics.setBackgroundColor(TerminalView.NORMAL_MENU_BG_COLOR);
                String horizontal = "─".repeat(component.text.length() + 2);
                graphics.putString(0, 0, "┌" + horizontal + "┐");
                graphics.putString(0, 1, "│ " + component.text + " │");
                graphics.putString(0, 2, "└" + horizontal + "┘");
            }
        }
    }

    /** Checkbox counterpart of {@link ValidatingTextBox}: reveals its row when it takes focus. */
    static CheckBox scrollingCheckBox(boolean checked) {
        CheckBox checkBox = new CheckBox() {
            @Override
            protected void afterEnterFocus(Interactable.FocusChangeDirection direction,
                    Interactable previouslyInFocus) {
                revealInColumn(this);
            }
        };
        checkBox.setChecked(checked);
        return checkBox;
    }

    /** Scrolls the ancestor {@link ScrollColumn}, when there is one, so {@code widget} shows. */
    private static void revealInColumn(Component widget) {
        for (Component parent = widget.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof ScrollColumn column) {
                column.reveal(widget);
                return;
            }
        }
    }

    /**
     * One-column section that fills the width and scrolls vertically to follow the focus: the
     * catalogue reaches 28 fields (Economy), so rows that do not fit stay reachable with Tab or the
     * arrow keys (the cramped 3-field grid was replaced by this column, #749). A thin bar on the
     * right edge marks the position while the content overflows.
     */
    static final class ScrollColumn extends Panel {

        private int offset;
        private boolean dirty = true;
        private boolean scrollable;

        ScrollColumn() {
            setLayoutManager(new ScrollingLayout());
            setRenderer(new ScrollingRenderer());
        }

        /** Scrolls the least amount that brings the row owning {@code descendant} into view. */
        void reveal(Component descendant) {
            List<Component> rows = getChildrenList();
            Component row = descendant;
            while (row != null && row.getParent() != this) {
                row = row.getParent();
            }
            int index = row == null ? -1 : rows.indexOf(row);
            int viewport = getSize().getRows();
            if (index < 0 || viewport <= 0) {
                return;
            }
            int top = 0;
            for (int i = 0; i < index; i++) {
                top += rowHeight(rows.get(i));
            }
            int next = revealOffset(offset, top, top + rowHeight(row), viewport, preferredRows());
            if (next != offset) {
                offset = next;
                dirty = true;
                invalidate();
            }
        }

        private int preferredRows() {
            int rows = 0;
            for (Component row : getChildrenList()) {
                rows += rowHeight(row);
            }
            return rows;
        }

        static int rowHeight(Component row) {
            return Math.max(1, row.getPreferredSize().getRows());
        }

        private final class ScrollingLayout implements LayoutManager {

            @Override
            public TerminalSize getPreferredSize(List<Component> components) {
                int width = 1;
                int height = 0;
                for (Component row : components) {
                    width = Math.max(width, row.getPreferredSize().getColumns());
                    height += rowHeight(row);
                }
                return new TerminalSize(width, height);
            }

            @Override
            public void doLayout(TerminalSize area, List<Component> components) {
                dirty = false;
                int total = 0;
                for (Component row : components) {
                    total += rowHeight(row);
                }
                scrollable = total > area.getRows();
                int width = Math.max(1, area.getColumns() - (scrollable ? 1 : 0));
                int y = -offset;
                for (Component row : components) {
                    int height = rowHeight(row);
                    row.setPosition(new TerminalPosition(0, y));
                    row.setSize(new TerminalSize(width, height));
                    y += height;
                }
            }

            @Override
            public boolean hasChanged() {
                return dirty;
            }
        }

        /** The default panel renderer plus the right-edge scroll marker. */
        private final class ScrollingRenderer implements ComponentRenderer<Panel> {

            private final ComponentRenderer<Panel> delegate = new DefaultPanelRenderer();

            @Override
            public TerminalSize getPreferredSize(Panel component) {
                return delegate.getPreferredSize(component);
            }

            @Override
            public void drawComponent(TextGUIGraphics graphics, Panel component) {
                delegate.drawComponent(graphics, component);
                drawScrollbar(graphics);
            }
        }

        /** Right-edge position marker: dim track, thumb in the normal foreground. */
        private void drawScrollbar(TextGUIGraphics graphics) {
            int viewport = getSize().getRows();
            int total = preferredRows();
            if (!scrollable || viewport <= 0 || total <= viewport) {
                return;
            }
            int column = getSize().getColumns() - 1;
            int thumbStart = offset * viewport / total;
            int thumbEnd = Math.max(thumbStart + 1, (offset + viewport) * viewport / total);
            for (int row = 0; row < viewport; row++) {
                boolean thumb = row >= thumbStart && row < thumbEnd;
                graphics.setForegroundColor(thumb ? TerminalView.NORMAL_MENU_FG_COLOR
                        : TerminalView.MENU_DISABLED_FG_COLOR);
                graphics.setBackgroundColor(TerminalView.NORMAL_MENU_BG_COLOR);
                graphics.putString(column, row, thumb ? "█" : "│");
            }
        }
    }

    /**
     * Water/mountain slider: a focusable bar that moves with Left/Right (1), PageUp/PageDown (5)
     * and Home/End (bounds). Its maximum shrinks so water + mountain never exceeds the catalogue
     * rule.
     */
    static final class PercentSlider extends AbstractInteractableComponent<PercentSlider> {

        private final String label;
        private final int valueColumn;
        private int value;
        private int max = SettingsCatalog.MAX_TERRAIN_PERCENT;
        private Runnable onChange = () -> {
        };

        PercentSlider(String label, int valueColumn, int value, Runnable onChange) {
            this.label = label;
            this.valueColumn = valueColumn;
            this.value = value;
            this.onChange = onChange;
        }

        int value() {
            return value;
        }

        /** Adjusts the upper bound (the other slider moved) and clamps the value without firing. */
        void setMaxAndValue(int max, int value) {
            this.max = Math.max(0, max);
            this.value = clamp(value);
            invalidate();
        }

        void setOnChange(Runnable onChange) {
            this.onChange = onChange;
        }

        private int clamp(int candidate) {
            return Math.max(0, Math.min(max, candidate));
        }

        @Override
        protected void afterEnterFocus(Interactable.FocusChangeDirection direction,
                Interactable previouslyInFocus) {
            revealInColumn(this);
        }

        @Override
        protected InteractableRenderer<PercentSlider> createDefaultRenderer() {
            return new Renderer();
        }

        @Override
        protected Result handleKeyStroke(KeyStroke keyStroke) {
            switch (keyStroke.getKeyType()) {
                case ArrowLeft:
                case ArrowDown:
                    return move(-1);
                case ArrowRight:
                case ArrowUp:
                    return move(1);
                case PageDown:
                    return move(-5);
                case PageUp:
                    return move(5);
                case Home:
                    return set(0);
                case End:
                    return set(max);
                default:
                    return super.handleKeyStroke(keyStroke);
            }
        }

        private Result move(int delta) {
            return set(value + delta);
        }

        private Result set(int candidate) {
            int clamped = clamp(candidate);
            if (clamped == value) {
                return Result.HANDLED;
            }
            value = clamped;
            invalidate();
            onChange.run();
            return Result.HANDLED;
        }

        private final class Renderer implements InteractableRenderer<PercentSlider> {
            @Override
            public TerminalSize getPreferredSize(PercentSlider component) {
                return new TerminalSize(valueColumn + 1 + SLIDER_BAR_WIDTH + 2 + 1 + 4, 1);
            }

            @Override
            public TerminalPosition getCursorLocation(PercentSlider component) {
                return null;
            }

            @Override
            public void drawComponent(TextGUIGraphics graphics, PercentSlider component) {
                TextColor labelColor = component.isFocused() ? TerminalView.SHORTCUT_COLOR
                        : TerminalView.NORMAL_MENU_FG_COLOR;
                graphics.setForegroundColor(labelColor);
                graphics.setBackgroundColor(TerminalView.NORMAL_MENU_BG_COLOR);
                graphics.putString(0, 0, alignedLabel(label, valueColumn));
                int x = valueColumn;
                int barWidth = sliderBarWidth(graphics.getSize().getColumns(), valueColumn);
                int filled = component.max == 0 ? 0
                        : (int) Math.round(component.value * barWidth
                                / (double) SettingsCatalog.MAX_TERRAIN_PERCENT);
                graphics.putString(x, 0, "[");
                graphics.setForegroundColor(
                        component.isFocused() ? TextColor.ANSI.CYAN_BRIGHT : TextColor.ANSI.GREEN);
                graphics.putString(x + 1, 0, "#".repeat(filled));
                graphics.setForegroundColor(TerminalView.MENU_DISABLED_FG_COLOR);
                graphics.putString(x + 1 + filled, 0, "-".repeat(barWidth - filled));
                graphics.setForegroundColor(labelColor);
                graphics.putString(x + 1 + barWidth, 0,
                        "] " + String.format("%3d%%", component.value));
            }
        }
    }

    /**
     * The 100 % stacked bar: water, field and mountain widths of the terrain distribution, spanning
     * the full Water/Mountain slider row (#755).
     */
    static final class StackedBar extends AbstractComponent<StackedBar> {
        private final TerminalPalette palette;
        private final int valueColumn;
        private int waterPercent;
        private int mountainPercent;

        StackedBar(TerminalPalette palette, int valueColumn, int waterPercent,
                int mountainPercent) {
            this.palette = palette;
            this.valueColumn = valueColumn;
            this.waterPercent = waterPercent;
            this.mountainPercent = mountainPercent;
        }

        void setPercentages(int waterPercent, int mountainPercent) {
            this.waterPercent = waterPercent;
            this.mountainPercent = mountainPercent;
            invalidate();
        }

        @Override
        protected ComponentRenderer<StackedBar> createDefaultRenderer() {
            return new Renderer();
        }

        private final class Renderer implements ComponentRenderer<StackedBar> {
            @Override
            public TerminalSize getPreferredSize(StackedBar component) {
                return new TerminalSize(valueColumn + SLIDER_BAR_WIDTH + 7, 1);
            }

            @Override
            public void drawComponent(TextGUIGraphics graphics, StackedBar component) {
                int width = stackedBarWidth(graphics.getSize().getColumns(), valueColumn);
                if (width < 3) {
                    return;
                }
                int[] widths =
                        stackedBarWidths(component.waterPercent, component.mountainPercent, width);
                int[] terrains = {TerrainPreview.WATER, TerrainPreview.GROUND, TerrainPreview.ROCK};
                int x = 0;
                for (int i = 0; i < 3; i++) {
                    graphics.setForegroundColor(terrainColor(palette, terrains[i]));
                    graphics.setBackgroundColor(TerminalView.NORMAL_MENU_BG_COLOR);
                    graphics.putString(x, 0, "█".repeat(widths[i]));
                    x += widths[i];
                }
            }
        }
    }

    /**
     * The adaptive terrain preview rendered with half blocks ({@code ▀}): the foreground colour is
     * the upper sample and the background colour the lower one, so each text row carries two sample
     * rows.
     */
    static final class TerrainPreviewPanel extends AbstractComponent<TerrainPreviewPanel> {
        private final TerminalPalette palette;
        private TerrainPreview.Grid grid;

        TerrainPreviewPanel(TerminalPalette palette) {
            this.palette = palette;
        }

        void setGrid(TerrainPreview.Grid grid) {
            this.grid = grid;
            invalidate();
        }

        @Override
        protected ComponentRenderer<TerrainPreviewPanel> createDefaultRenderer() {
            return new Renderer();
        }

        private final class Renderer implements ComponentRenderer<TerrainPreviewPanel> {
            @Override
            public TerminalSize getPreferredSize(TerrainPreviewPanel component) {
                if (component.grid == null) {
                    return new TerminalSize(1, 1);
                }
                return new TerminalSize(component.grid.columns(), (component.grid.rows() + 1) / 2);
            }

            @Override
            public void drawComponent(TextGUIGraphics graphics, TerrainPreviewPanel component) {
                TerrainPreview.Grid sample = component.grid;
                if (sample == null) {
                    return;
                }
                for (int row = 0; row < (sample.rows() + 1) / 2; row++) {
                    int topRow = row * 2;
                    int bottomRow = Math.min(topRow + 1, sample.rows() - 1);
                    for (int column = 0; column < sample.columns(); column++) {
                        graphics.setForegroundColor(
                                terrainColor(palette, sample.cell(column, topRow)));
                        graphics.setBackgroundColor(
                                terrainColor(palette, sample.cell(column, bottomRow)));
                        graphics.setCharacter(column, row, '▀');
                    }
                }
                graphics.setBackgroundColor(TerminalView.NORMAL_MENU_BG_COLOR);
            }
        }
    }

    /**
     * Dark theme for the whole dialog (#757): the base Lanterna theme paints panels black-on-white,
     * which is what made the dialog look bright. Every definition keeps its SGRs but swaps to the
     * 2D client's menu palette: white on black, white on the selected blue when focused and grey on
     * black when disabled.
     */
    static Theme darkTheme(Theme base) {
        return new DelegatingTheme(base) {
            @Override
            public ThemeDefinition getDefaultDefinition() {
                return darkDefinition(super.getDefaultDefinition());
            }

            @Override
            public ThemeDefinition getDefinition(Class<?> clazz) {
                return darkDefinition(super.getDefinition(clazz));
            }
        };
    }

    private static ThemeDefinition darkDefinition(ThemeDefinition definition) {
        return new DelegatingThemeDefinition(definition) {
            @Override
            public ThemeStyle getNormal() {
                return restyled(super.getNormal(), TerminalView.NORMAL_MENU_FG_COLOR,
                        TerminalView.NORMAL_MENU_BG_COLOR);
            }

            @Override
            public ThemeStyle getPreLight() {
                return restyled(super.getPreLight(), TerminalView.NORMAL_MENU_FG_COLOR,
                        TerminalView.NORMAL_MENU_BG_COLOR);
            }

            @Override
            public ThemeStyle getSelected() {
                return restyled(super.getSelected(), TerminalView.NORMAL_MENU_FG_COLOR,
                        TerminalView.SELECTED_BG_COLOR);
            }

            @Override
            public ThemeStyle getActive() {
                return restyled(super.getActive(), TerminalView.NORMAL_MENU_FG_COLOR,
                        TerminalView.SELECTED_BG_COLOR);
            }

            @Override
            public ThemeStyle getInsensitive() {
                return restyled(super.getInsensitive(), TerminalView.MENU_DISABLED_FG_COLOR,
                        TerminalView.NORMAL_MENU_BG_COLOR);
            }
        };
    }

    /** Copy of {@code style} with the dialog's dark colours; SGRs (bold...) are preserved. */
    private static ThemeStyle restyled(ThemeStyle style, TextColor foreground,
            TextColor background) {
        return new DefaultMutableThemeStyle(style).setForeground(foreground)
                .setBackground(background);
    }

    private NewGameDialog() {}
}
