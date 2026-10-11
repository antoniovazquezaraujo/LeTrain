package letrain.mvp.impl.terminal;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.AbstractComponent;
import com.googlecode.lanterna.gui2.AbstractInteractableComponent;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.CheckBox;
import com.googlecode.lanterna.gui2.Component;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.Container;
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
import com.googlecode.lanterna.screen.Screen;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import letrain.ground.TerrainCalibrator;
import letrain.ground.TerrainPreview;
import letrain.mvp.NewGameText;
import letrain.settings.NewGameForm;
import letrain.settings.SettingCategory;
import letrain.settings.SettingDefinition;
import letrain.settings.SettingValueType;
import letrain.settings.SettingsCatalog;
import letrain.visitor.terminal.TerminalPalette;

/**
 * 2D New Game dialog (S2, issue #746; layout polished for #749). Full-screen, borderless, built
 * from the shared catalogue:
 *
 * <ul>
 * <li><b>Terrain</b> tab (opens first): seed (random, editable and copyable), water/mountain
 * sliders honouring the catalogue rule (water + mountain ≤ 95 %, the field is the remainder), a 100
 * % stacked bar, the target/measured proportions and an adaptive preview that samples
 * {@link TerrainPreview} with the same calibrated thresholds the world generator uses. The terrain
 * controls live only in this tab: they are no longer pinned above every section (#749).</li>
 * <li><b>Sections</b>: Game, Economy, Construction and UI read
 * {@link SettingsCatalog#definitions()} as a single scrolling column of label + value + unit rows
 * (the 3-field grid felt cramped, #749).</li>
 * <li><b>Start</b> validates the whole form and returns the settings map to the presenter, which
 * builds the world via {@code Model.newGame}. <b>Esc</b> (or Cancel) returns nothing.
 * <b>Alt+1..5</b> switch tabs; <b>Alt+N</b> draws a seed, <b>Alt+S</b> starts, <b>Alt+C</b> cancels
 * (the old dialog advertised no keys and answered none, #749).</li>
 * </ul>
 *
 * <p>
 * The layout is a pure function of the terminal size ({@link #previewPlan(int, int)} and
 * {@link #revealOffset(int, int, int, int, int)}), so the adaptive behaviour is testable without a
 * terminal.
 */
final class NewGameDialog {

    /** Minimum terminal for any preview. */
    static final int MIN_COLUMNS = 80;
    static final int MIN_ROWS = 24;

    /** Rows the tab bar takes: always visible on top, every section is swapped below it. */
    static final int TAB_ROWS = 1;
    /** Rows the Start/Cancel footer takes. */
    static final int FOOTER_ROWS = 1;
    /**
     * Rows the terrain controls above the preview take inside the Terrain tab: seed, water,
     * mountain, bar and status line.
     */
    static final int TERRAIN_CHROME_ROWS = 5;

    /** Width of one slider's bar, in cells. */
    static final int SLIDER_BAR_WIDTH = 40;
    /** Width of the seed text box, in cells. */
    static final int SEED_BOX_WIDTH = 10;

    private static final int LABEL_WIDTH = 10;
    /** Label column of a catalogue row; values line up on the right of it. */
    private static final int FIELD_LABEL_WIDTH = 30;
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

    /** Footer hint: the Alt access keys (tabs are numeric, so all letters stay free). */
    static final String HOTKEY_HINT = "Alt+1-5 tabs · Alt+S start · Alt+C cancel";

    /** Preview sizes: {@code sampleRows} is the sampled grid height; each text row shows two. */
    record PreviewPlan(int columns, int sampleRows) {
        int textRows() {
            return (sampleRows + 1) / 2;
        }
    }

    /**
     * How much preview fits in a terminal of {@code columns x rows}: {@code null} below 80x24
     * (concept §7). The sample grid is square and covers the whole calibration window, so the
     * half-block render (two sample rows per text row, each column a full cell wide) shows the
     * world with its 1:1 cells. A screen-wide grid stretched the world horizontally (#749).
     */
    static PreviewPlan previewPlan(int columns, int rows) {
        if (columns < MIN_COLUMNS || rows < MIN_ROWS) {
            return null;
        }
        int contentRows = rows - TAB_ROWS - FOOTER_ROWS;
        int previewTextRows = contentRows - TERRAIN_CHROME_ROWS;
        if (previewTextRows < 2) {
            return null;
        }
        int sample = Math.min(columns - PREVIEW_MARGIN_COLUMNS, previewTextRows * 2);
        return new PreviewPlan(sample, sample);
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

    /** Label of one catalogue field: display name plus a compact range hint ({@code (0..95)}). */
    static String fieldLabel(SettingDefinition definition) {
        if (definition.min() != null && definition.max() != null) {
            return definition.displayName() + " (" + number(definition.min()) + ".."
                    + number(definition.max()) + ")";
        }
        if (definition.min() != null) {
            return definition.displayName() + " (" + number(definition.min()) + "+)";
        }
        if (definition.max() != null) {
            return definition.displayName() + " (.." + number(definition.max()) + ")";
        }
        return definition.displayName();
    }

    /** Unit shown after the value; {@code "ticks"} in full, the single column has room for it. */
    static String fieldUnit(SettingDefinition definition) {
        return definition.unitValue().orElse("");
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

    /**
     * Tab selected by an Alt access key: {@code '1'..'5'} in tab order, empty for any other key.
     */
    static Optional<SettingCategory> tabHotkey(char key, List<SettingCategory> tabs) {
        if (key < '1' || key > '9') {
            return Optional.empty();
        }
        int index = key - '1';
        return index < tabs.size() ? Optional.of(tabs.get(index)) : Optional.empty();
    }

    /**
     * Opens the modal dialog and returns the validated settings, or {@code null} when the player
     * cancels (Esc/Cancel).
     */
    static Map<String, String> show(Screen screen, Map<String, String> currentSettings) {
        NewGameForm form = NewGameForm.forNewGame(currentSettings);
        MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);
        TerminalPalette palette = TerminalPalette.detect();
        BasicWindow window = new BasicWindow();
        window.setHints(List.of(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS));
        window.setCloseWindowWithEscape(true);

        final Map<String, String>[] result = new Map[] {null};
        final Map<String, Supplier<String>> readers = new LinkedHashMap<>();

        Panel root = new Panel(new BorderLayout());
        Panel content = new Panel(new BorderLayout());

        // --- Terrain tab: seed, sliders, bar, status and preview (only here since #749) -----
        final int[] seed = {form.seed()};
        Panel seedRow = new Panel(new LinearLayout(Direction.HORIZONTAL));
        seedRow.addComponent(new Label(NewGameText.SEED_LABEL).setLabelWidth(LABEL_WIDTH));
        TextBox seedBox = new TextBox(String.valueOf(seed[0]));
        seedBox.setPreferredSize(new TerminalSize(SEED_BOX_WIDTH, 1));
        seedRow.addComponent(seedBox);
        seedRow.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        Runnable drawSeed = () -> {
            seed[0] = NewGameForm.nextSeed(new Random());
            seedBox.setText(String.valueOf(seed[0]));
        };
        seedRow.addComponent(mnemonicButton(NewGameText.NEW_SEED, 'N', drawSeed));

        PercentSlider water =
                new PercentSlider(NewGameText.WATER_LABEL, form.waterPercent(), () -> {
                });
        PercentSlider mountain =
                new PercentSlider(NewGameText.MOUNTAIN_LABEL, form.mountainPercent(), () -> {
                });
        StackedBar bar = new StackedBar(palette, form.waterPercent(), form.mountainPercent());

        Label status = new Label("");

        // The preview updates on every slider/seed change: same noise, thresholds and mapping as
        // the world generator.
        final TerrainPreviewPanel previewPanel = new TerrainPreviewPanel(palette);
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

        ScrollColumn terrain = new ScrollColumn();
        terrain.addComponent(seedRow);
        terrain.addComponent(water);
        terrain.addComponent(mountain);
        terrain.addComponent(singleRowComponent(bar));
        terrain.addComponent(status);
        if (plan != null) {
            terrain.addComponent(previewPanel);
        } else {
            terrain.addComponent(new Label(NewGameText.NO_PREVIEW));
        }

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
        for (int i = 0; i < tabs.size(); i++) {
            SettingCategory category = tabs.get(i);
            Button button = new Button(category.displayName());
            button.setRenderer(tabRenderer(category, i, active));
            button.addListener(triggered -> {
                active[0] = category;
                showSection(content, sections, category, window);
            });
            tabBar.addComponent(button);
        }
        showSection(content, sections, active[0], window);

        // --- Footer and window -------------------------------------------------------------
        Runnable startAction = () -> {
            try {
                form.setSeed(seedBox.getText());
                for (Map.Entry<String, Supplier<String>> entry : readers.entrySet()) {
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
        };

        Panel footer = new Panel(new LinearLayout(Direction.HORIZONTAL));
        footer.addComponent(mnemonicButton(NewGameText.START, 'S', startAction));
        footer.addComponent(mnemonicButton(NewGameText.CANCEL, 'C', window::close));
        footer.addComponent(new Label("  " + HOTKEY_HINT));

        // Alt access keys: tabs by number, actions by letter (see the footer hint).
        window.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onInput(Window w, KeyStroke keyStroke, AtomicBoolean deliverEvent) {
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
                } else if (key == 'n') {
                    drawSeed.run();
                    deliverEvent.set(false);
                }
            }
        });

        root.addComponent(tabBar, BorderLayout.Location.TOP);
        root.addComponent(content, BorderLayout.Location.CENTER);
        root.addComponent(footer, BorderLayout.Location.BOTTOM);

        window.setComponent(root);
        recalibrate.run();
        gui.addWindowAndWait(window);
        return result[0];
    }

    /** Swaps the active section into the content area; the tab bar repaints its active style. */
    private static void showSection(Panel content, Map<SettingCategory, Panel> sections,
            SettingCategory active, BasicWindow window) {
        content.removeAllComponents();
        Panel section = sections.get(active);
        content.addComponent(section, BorderLayout.Location.CENTER);
        // If the focus lived inside the section just swapped out, hand it to the new section's
        // first field: keys must not keep editing a widget that is no longer on screen.
        Interactable focused = window.getFocusedInteractable();
        if (focused != null && window.getComponent() instanceof Container root
                && !focused.isInside(root)) {
            window.setFocusedInteractable(section.nextFocus(null));
        }
        window.invalidate();
    }

    /** Wraps a 1-row component so a BorderLayout stretches it to the available width. */
    private static Panel singleRowComponent(Component component) {
        Panel panel = new Panel(new BorderLayout());
        panel.addComponent(component, BorderLayout.Location.CENTER);
        return panel;
    }

    /** Button with its Alt mnemonic highlighted, the same style as the editor footer. */
    private static Button mnemonicButton(String label, char mnemonic, Runnable action) {
        Button button = new Button(label, action);
        button.setRenderer(TerminalView.mnemonicRenderer(mnemonic));
        return button;
    }

    /** `< 1 Terrain >` tabs: the active one is reversed, the Alt number stays highlighted. */
    private static InteractableRenderer<Button> tabRenderer(SettingCategory category, int index,
            SettingCategory[] active) {
        return new InteractableRenderer<Button>() {
            @Override
            public TerminalSize getPreferredSize(Button component) {
                return new TerminalSize(component.getLabel().length() + 7, 1);
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
                graphics.putString(0, 0, "< " + (index + 1) + " " + component.getLabel() + " >");
                if (isActive) {
                    graphics.enableModifiers(com.googlecode.lanterna.SGR.BOLD);
                } else {
                    graphics.setForegroundColor(TerminalView.SHORTCUT_COLOR);
                }
                graphics.putString(2, 0, String.valueOf(index + 1));
                graphics.disableModifiers(com.googlecode.lanterna.SGR.BOLD);
            }

            @Override
            public TerminalPosition getCursorLocation(Button component) {
                return null;
            }
        };
    }

    /**
     * Builds the single-column body of one catalogue section: one field per row, label + value +
     * unit. The text of every widget is registered in {@code readers} so Start collects the form in
     * one place.
     */
    private static ScrollColumn buildSection(SettingCategory category, NewGameForm form,
            Map<String, Supplier<String>> readers) {
        ScrollColumn column = new ScrollColumn();
        for (SettingDefinition definition : SettingsCatalog.definitions()) {
            if (definition.category() == category) {
                column.addComponent(fieldRow(definition, form, readers));
            }
        }
        return column;
    }

    /** Label + value (+ unit) of one catalogue field. */
    private static Panel fieldRow(SettingDefinition definition, NewGameForm form,
            Map<String, Supplier<String>> readers) {
        Panel row = new Panel(new LinearLayout(Direction.HORIZONTAL));
        row.addComponent(new Label(fieldLabel(definition)).setLabelWidth(FIELD_LABEL_WIDTH));
        if (definition.type() == SettingValueType.BOOLEAN) {
            CheckBox checkBox =
                    scrollingCheckBox(Boolean.parseBoolean(form.value(definition.key())));
            row.addComponent(checkBox);
            readers.put(definition.key(), () -> checkBox.isChecked() ? "true" : "false");
        } else {
            TextBox textBox = scrollingTextBox(form.value(definition.key()));
            row.addComponent(textBox);
            String unit = fieldUnit(definition);
            if (!unit.isEmpty()) {
                row.addComponent(new Label(" " + unit));
            }
            readers.put(definition.key(), textBox::getText);
        }
        return row;
    }

    /** Text box that scrolls its column when it takes focus (Tab/arrow form navigation). */
    private static TextBox scrollingTextBox(String value) {
        return new TextBox(new TerminalSize(FIELD_WIDTH, 1), value) {
            @Override
            protected void afterEnterFocus(Interactable.FocusChangeDirection direction,
                    Interactable previouslyInFocus) {
                revealInColumn(this);
            }
        };
    }

    /** Checkbox counterpart of {@link #scrollingTextBox}. */
    private static CheckBox scrollingCheckBox(boolean checked) {
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
        private final TerminalPalette palette;
        private int waterPercent;
        private int mountainPercent;

        StackedBar(TerminalPalette palette, int waterPercent, int mountainPercent) {
            this.palette = palette;
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
                int xOffset = Math.max(0, (graphics.getSize().getColumns() - sample.columns()) / 2);
                for (int row = 0; row < (sample.rows() + 1) / 2; row++) {
                    int topRow = row * 2;
                    int bottomRow = Math.min(topRow + 1, sample.rows() - 1);
                    for (int column = 0; column < sample.columns(); column++) {
                        graphics.setForegroundColor(
                                terrainColor(palette, sample.cell(column, topRow)));
                        graphics.setBackgroundColor(
                                terrainColor(palette, sample.cell(column, bottomRow)));
                        graphics.setCharacter(xOffset + column, row, '▀');
                    }
                }
                graphics.setBackgroundColor(TerminalView.NORMAL_MENU_BG_COLOR);
            }
        }
    }

    private NewGameDialog() {}
}
