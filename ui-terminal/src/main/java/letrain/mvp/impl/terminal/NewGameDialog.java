package letrain.mvp.impl.terminal;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.gui2.AbstractComponent;
import com.googlecode.lanterna.gui2.AbstractInteractableComponent;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.CheckBox;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.GridLayout;
import com.googlecode.lanterna.gui2.InteractableRenderer;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.Screen;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import letrain.ground.TerrainCalibrator;
import letrain.ground.TerrainPreview;
import letrain.mvp.NewGameText;
import letrain.settings.NewGameForm;
import letrain.settings.SettingCategory;
import letrain.settings.SettingDefinition;
import letrain.settings.SettingValueType;
import letrain.settings.SettingsCatalog;

/**
 * 2D New Game dialog (S2, issue #746). Full-screen, borderless, built from the shared catalogue:
 *
 * <ul>
 * <li><b>Terrain</b> (always visible): seed (random, editable and copyable), water/mountain sliders
 * honouring the catalogue rule (water + mountain ≤ 95 %, the field is the remainder), a 100 %
 * stacked bar and an adaptive preview (< 80x24: none; 80x24: compact; ≥120x30: large) that samples
 * {@link TerrainPreview} with the same calibrated thresholds the world generator uses. Target and
 * measured proportions are always shown, preview or not.</li>
 * <li><b>Sections</b>: Game (world), Economy, Construction and UI read
 * {@link SettingsCatalog#definitions()} — numeric fields with their range/unit and checkboxes for
 * booleans, no more.</li>
 * <li><b>Start</b> validates the whole form and returns the settings map to the presenter, which
 * builds the world via {@code Model.newGame}. <b>Esc</b> (or Cancel) returns nothing.</li>
 * </ul>
 *
 * <p>
 * The layout is a pure function of the terminal size ({@link #previewPlan(int, int)} and the row
 * constants), so the adaptive behaviour is testable without a terminal.
 */
final class NewGameDialog {

    /** Minimum terminal for any preview. */
    static final int MIN_COLUMNS = 80;
    static final int MIN_ROWS = 24;
    /** Terminal from which the preview is considered large. */
    static final int LARGE_COLUMNS = 120;
    static final int LARGE_ROWS = 30;

    /** Rows the always-visible terrain header takes: tabs, seed, 2 sliders, bar and status. */
    static final int HEADER_ROWS = 6;
    /** Rows the Start/Cancel footer takes. */
    static final int FOOTER_ROWS = 1;

    /** Width of one slider's bar, in cells. */
    static final int SLIDER_BAR_WIDTH = 40;
    /** Width of the seed text box, in cells. */
    static final int SEED_BOX_WIDTH = 10;

    private static final int LABEL_WIDTH = 10;
    /** Wide enough for the longest default ({@code 1000000.0}) plus the caret. */
    private static final int FIELD_WIDTH = 10;

    /** Preview sizes: {@code sampleRows} is the sampled grid height; each text row shows two. */
    record PreviewPlan(boolean large, int columns, int sampleRows) {
        int textRows() {
            return (sampleRows + 1) / 2;
        }
    }

    /**
     * How much preview fits in a terminal of {@code columns x rows}: {@code null} below 80x24
     * (concept §7), compact from there and large from 120x30. The preview is 4 columns narrower
     * than the terminal (margin) and as tall as the free space between header and footer, in the
     * two sample rows per text row of the half-block rendering.
     */
    static PreviewPlan previewPlan(int columns, int rows) {
        if (columns < MIN_COLUMNS || rows < MIN_ROWS) {
            return null;
        }
        int textRows = rows - HEADER_ROWS - FOOTER_ROWS;
        if (textRows < 2) {
            return null;
        }
        boolean large = columns >= LARGE_COLUMNS && rows >= LARGE_ROWS;
        return new PreviewPlan(large, columns - 4, textRows * 2);
    }

    /**
     * Segment widths {@code [water, mountain, field]} of the 100 % stacked bar, totalling exactly
     * {@code width} cells (largest remainder, so no segment vanishes while it has cells to claim).
     */
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

    /** Label of one catalogue field: display name plus a compact range/unit hint. */
    static String fieldLabel(SettingDefinition definition) {
        String unit = definition.unitValue().map(u -> " " + shortUnit(u)).orElse("");
        if (definition.min() != null && definition.max() != null) {
            return definition.displayName() + " (" + number(definition.min()) + ".."
                    + number(definition.max()) + unit + ")";
        }
        if (definition.min() != null) {
            return definition.displayName() + " (" + number(definition.min()) + "+" + unit + ")";
        }
        if (definition.max() != null) {
            return definition.displayName() + " (.." + number(definition.max()) + unit + ")";
        }
        return definition.displayName();
    }

    private static String shortUnit(String unit) {
        return "ticks".equals(unit) ? "t" : unit;
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /**
     * Opens the modal dialog and returns the validated settings, or {@code null} when the player
     * cancels (Esc/Cancel).
     */
    static Map<String, String> show(Screen screen, Map<String, String> currentSettings) {
        NewGameForm form = NewGameForm.forNewGame(currentSettings);
        MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);
        BasicWindow window = new BasicWindow();
        window.setHints(List.of(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS));
        window.setCloseWindowWithEscape(true);

        final Map<String, String>[] result = new Map[] {null};
        final Map<String, java.util.function.Supplier<String>> readers = new LinkedHashMap<>();

        Panel root = new Panel(new BorderLayout());
        Panel header = new Panel(new LinearLayout(Direction.VERTICAL));

        // --- Terrain block (always visible) ------------------------------------------------
        final int[] seed = {form.seed()};
        Panel seedRow = new Panel(new LinearLayout(Direction.HORIZONTAL));
        seedRow.addComponent(new Label(NewGameText.SEED_LABEL).setLabelWidth(LABEL_WIDTH));
        TextBox seedBox = new TextBox(String.valueOf(seed[0]));
        seedBox.setPreferredSize(new TerminalSize(SEED_BOX_WIDTH, 1));
        seedRow.addComponent(seedBox);
        seedRow.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        seedRow.addComponent(new Button(NewGameText.NEW_SEED, () -> {
            seed[0] = NewGameForm.nextSeed(new Random());
            seedBox.setText(String.valueOf(seed[0]));
        }));

        PercentSlider water =
                new PercentSlider(NewGameText.WATER_LABEL, form.waterPercent(), () -> {
                });
        PercentSlider mountain =
                new PercentSlider(NewGameText.MOUNTAIN_LABEL, form.mountainPercent(), () -> {
                });
        StackedBar bar = new StackedBar(form.waterPercent(), form.mountainPercent());

        Label status = new Label("");

        // The preview updates on every slider/seed change: same noise, thresholds and mapping as
        // the world generator.
        final TerrainPreviewPanel previewPanel = new TerrainPreviewPanel();
        final PreviewPlan plan = previewPlan(screen.getTerminalSize().getColumns(),
                screen.getTerminalSize().getRows());

        Runnable recalibrate = () -> {
            TerrainCalibrator.Calibration calibration = TerrainCalibrator.calibrate(seed[0],
                    form.waterPercent(), form.mountainPercent());
            bar.setPercentages(form.waterPercent(), form.mountainPercent());
            status.setText(NewGameText.terrainStatusLine(form.waterPercent(),
                    form.mountainPercent(), calibration.measuredWaterPercent(),
                    calibration.measuredMountainPercent()));
            if (plan != null) {
                previewPanel.setGrid(TerrainPreview.sample(seed[0], form.waterPercent(),
                        form.mountainPercent(), plan.columns(), plan.sampleRows()));
            }
        };

        Runnable onSliderChange = () -> {
            int newWater = water.value();
            int newMountain = mountain.value();
            form.setWaterPercent(newWater);
            form.setMountainPercent(newMountain);
            water.setMaxAndValue(SettingsCatalog.MAX_TERRAIN_PERCENT - form.mountainPercent(),
                    form.waterPercent());
            mountain.setMaxAndValue(SettingsCatalog.MAX_TERRAIN_PERCENT - form.waterPercent(),
                    form.mountainPercent());
            recalibrate.run();
            window.invalidate();
        };
        water.setOnChange(onSliderChange);
        mountain.setOnChange(onSliderChange);
        seedBox.setTextChangeListener((text, ignored) -> {
            // Fired by typing and by the `new` button, which also recalibrates the preview.
            try {
                seed[0] = Integer.parseInt(text.trim());
                recalibrate.run();
                window.invalidate();
            } catch (NumberFormatException e) {
                // Keep the last valid seed for the preview; Start validates the field.
            }
        });

        header.addComponent(seedRow);
        header.addComponent(water);
        header.addComponent(mountain);
        header.addComponent(singleRowComponent(bar),
                LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
        header.addComponent(status);

        // --- Sections ----------------------------------------------------------------------
        final List<SettingCategory> tabs = List.of(SettingCategory.TERRAIN, SettingCategory.WORLD,
                SettingCategory.ECONOMY, SettingCategory.CONSTRUCTION, SettingCategory.UI);
        int fieldsPerRow = screen.getTerminalSize().getColumns() >= LARGE_COLUMNS ? 3 : 2;
        Map<SettingCategory, Panel> sections = new EnumMap<>(SettingCategory.class);
        Panel terrainSection = new Panel(new BorderLayout());
        if (plan != null) {
            terrainSection.addComponent(previewPanel, BorderLayout.Location.CENTER);
        } else {
            terrainSection.addComponent(new Label(NewGameText.NO_PREVIEW),
                    BorderLayout.Location.TOP);
        }
        sections.put(SettingCategory.TERRAIN, terrainSection);
        for (SettingCategory category : tabs) {
            if (category != SettingCategory.TERRAIN) {
                sections.put(category, buildSection(category, form, fieldsPerRow, readers));
            }
        }

        Panel content = new Panel(new BorderLayout());
        Panel tabBar = new Panel(new LinearLayout(Direction.HORIZONTAL));
        final SettingCategory[] active = {SettingCategory.TERRAIN};
        List<Button> tabButtons = new ArrayList<>();
        for (SettingCategory category : tabs) {
            Button button = new Button(category.displayName());
            button.addListener(triggered -> {
                active[0] = category;
                showSection(content, sections, active[0], tabButtons, tabs, window);
            });
            tabButtons.add(button);
            tabBar.addComponent(button);
        }
        showSection(content, sections, active[0], tabButtons, tabs, window);
        header.addComponent(0, tabBar);

        // --- Footer and window -------------------------------------------------------------
        Panel footer = new Panel(new LinearLayout(Direction.HORIZONTAL));
        footer.addComponent(new Button(NewGameText.START, () -> {
            try {
                form.setSeed(seedBox.getText());
                for (Map.Entry<String, java.util.function.Supplier<String>> entry : readers
                        .entrySet()) {
                    form.setValue(entry.getKey(), entry.getValue().get());
                }
                form.setWaterPercent(water.value());
                form.setMountainPercent(mountain.value());
                result[0] = form.toSettings();
                window.close();
            } catch (IllegalArgumentException e) {
                new MessageDialogBuilder().setTitle(NewGameText.INVALID_TITLE)
                        .setText(String.valueOf(e.getMessage())).addButton(MessageDialogButton.OK)
                        .build().showDialog(gui);
            }
        }));
        footer.addComponent(new Button(NewGameText.CANCEL, window::close));
        footer.addComponent(new Label("  Esc: cancel"));

        root.addComponent(header, BorderLayout.Location.TOP);
        root.addComponent(content, BorderLayout.Location.CENTER);
        root.addComponent(footer, BorderLayout.Location.BOTTOM);

        window.setComponent(root);
        recalibrate.run();
        gui.addWindowAndWait(window);
        return result[0];
    }

    /** Swaps the active section into the content area and marks the active tab with brackets. */
    private static void showSection(Panel content, Map<SettingCategory, Panel> sections,
            SettingCategory active, List<Button> tabButtons, List<SettingCategory> tabs,
            BasicWindow window) {
        content.removeAllComponents();
        content.addComponent(sections.get(active), BorderLayout.Location.CENTER);
        for (int i = 0; i < tabs.size(); i++) {
            SettingCategory category = tabs.get(i);
            tabButtons.get(i).setLabel(category == active ? "[" + category.displayName() + "]"
                    : category.displayName());
        }
        window.invalidate();
    }

    /** Wraps a 1-row component so a BorderLayout stretches it to the available width. */
    private static Panel singleRowComponent(com.googlecode.lanterna.gui2.Component component) {
        Panel panel = new Panel(new BorderLayout());
        panel.addComponent(component, BorderLayout.Location.CENTER);
        return panel;
    }

    /**
     * Builds the field grid of one catalogue section: label + text box (or checkbox for booleans),
     * {@code fieldsPerRow} per row. The text of every widget is registered in {@code readers} so
     * Start collects the form in one place.
     */
    private static Panel buildSection(SettingCategory category, NewGameForm form, int fieldsPerRow,
            Map<String, java.util.function.Supplier<String>> readers) {
        Panel panel = new Panel(new GridLayout(fieldsPerRow * 2));
        for (SettingDefinition definition : SettingsCatalog.definitions()) {
            if (definition.category() != category) {
                continue;
            }
            panel.addComponent(new Label(fieldLabel(definition)));
            if (definition.type() == SettingValueType.BOOLEAN) {
                CheckBox checkBox = new CheckBox();
                checkBox.setChecked(Boolean.parseBoolean(form.value(definition.key())));
                panel.addComponent(checkBox);
                readers.put(definition.key(), () -> checkBox.isChecked() ? "true" : "false");
            } else {
                TextBox textBox =
                        new TextBox(new TerminalSize(FIELD_WIDTH, 1), form.value(definition.key()));
                panel.addComponent(textBox);
                readers.put(definition.key(), textBox::getText);
            }
        }
        Panel wrapper = new Panel(new BorderLayout());
        wrapper.addComponent(panel, BorderLayout.Location.TOP);
        return wrapper;
    }

    /**
     * Water/mountain slider: a focusable bar that moves with Left/Right (1), PageUp/PageDown (5)
     * and Home/End (bounds). Its maximum shrinks so water + mountain never exceeds the catalogue
     * rule.
     */
    static final class PercentSlider extends AbstractInteractableComponent<PercentSlider> {

        private final String label;
        private int value;
        private int max = SettingsCatalog.MAX_TERRAIN_PERCENT;
        private Runnable onChange = () -> {
        };

        PercentSlider(String label, int value, Runnable onChange) {
            this.label = label;
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
                return new TerminalSize(LABEL_WIDTH + 1 + SLIDER_BAR_WIDTH + 2 + 1 + 4, 1);
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
                String padded = label + " ".repeat(Math.max(0, LABEL_WIDTH - label.length()));
                graphics.putString(0, 0, padded);
                int x = LABEL_WIDTH + 1;
                int filled = component.max == 0 ? 0
                        : (int) Math.round(component.value * SLIDER_BAR_WIDTH
                                / (double) SettingsCatalog.MAX_TERRAIN_PERCENT);
                graphics.putString(x, 0, "[");
                graphics.setForegroundColor(
                        component.isFocused() ? TextColor.ANSI.CYAN_BRIGHT : TextColor.ANSI.GREEN);
                graphics.putString(x + 1, 0, "#".repeat(filled));
                graphics.setForegroundColor(TerminalView.MENU_DISABLED_FG_COLOR);
                graphics.putString(x + 1 + filled, 0, "-".repeat(SLIDER_BAR_WIDTH - filled));
                graphics.setForegroundColor(labelColor);
                graphics.putString(x + 1 + SLIDER_BAR_WIDTH, 0,
                        "] " + String.format("%3d%%", component.value));
            }
        }
    }

    /** The 100 % stacked bar: water, field and mountain widths of the terrain distribution. */
    static final class StackedBar extends AbstractComponent<StackedBar> {
        private int waterPercent;
        private int mountainPercent;

        StackedBar(int waterPercent, int mountainPercent) {
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

        private static TextColor color(int terrain) {
            switch (terrain) {
                case TerrainPreview.WATER:
                    return TextColor.ANSI.BLUE;
                case TerrainPreview.ROCK:
                    return TextColor.ANSI.WHITE;
                default:
                    return TextColor.ANSI.GREEN;
            }
        }

        private final class Renderer implements ComponentRenderer<StackedBar> {
            @Override
            public TerminalSize getPreferredSize(StackedBar component) {
                return new TerminalSize(40, 1);
            }

            @Override
            public void drawComponent(TextGUIGraphics graphics, StackedBar component) {
                int width = graphics.getSize().getColumns();
                if (width < 3) {
                    return;
                }
                int[] widths =
                        stackedBarWidths(component.waterPercent, component.mountainPercent, width);
                int[] terrains = {TerrainPreview.WATER, TerrainPreview.GROUND, TerrainPreview.ROCK};
                int x = 0;
                for (int i = 0; i < 3; i++) {
                    graphics.setForegroundColor(color(terrains[i]));
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
        private TerrainPreview.Grid grid;

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
                int xOffset = Math.max(0, (graphics.getSize().getColumns() - sample.columns()) / 2);
                for (int row = 0; row < (sample.rows() + 1) / 2; row++) {
                    int topRow = row * 2;
                    int bottomRow = Math.min(topRow + 1, sample.rows() - 1);
                    for (int column = 0; column < sample.columns(); column++) {
                        graphics.setForegroundColor(StackedBar.color(sample.cell(column, topRow)));
                        graphics.setBackgroundColor(
                                StackedBar.color(sample.cell(column, bottomRow)));
                        graphics.setCharacter(xOffset + column, row, '▀');
                    }
                }
                graphics.setBackgroundColor(TerminalView.NORMAL_MENU_BG_COLOR);
            }
        }
    }
}
