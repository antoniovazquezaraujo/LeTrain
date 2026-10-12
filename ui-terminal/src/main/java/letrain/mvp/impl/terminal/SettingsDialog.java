package letrain.mvp.impl.terminal;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.CheckBox;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowListenerAdapter;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import letrain.mvp.SettingsText;
import letrain.settings.SettingApplyState;
import letrain.settings.SettingCategory;
import letrain.settings.SettingDefinition;
import letrain.settings.SettingValueType;
import letrain.settings.SettingsApplyResult;
import letrain.settings.SettingsCatalog;

/**
 * 2D in-game Settings dialog (S3b, #764; concept §5.2/D9/D10), opened from File → Settings while a
 * game runs. It shares the New Game form conventions ({@link NewGameDialog}): tabs with integrated
 * mnemonics, one value column per page, the left margin, the black theme, Enter/Tab commit with
 * range tooltips, Tab walking the active page fields plus the footer and Alt+letter/Ctrl+Tab
 * switching tabs.
 *
 * <p>
 * Every field carries its apply-state mark: green {@code ●} for LIVE, yellow {@code ●} for REAPPLY
 * (both editable), red {@code ●} for NEW_GAME and white {@code ●} for scenario-locked keys (both
 * disabled with their reason appended). The legend under the tabs repeats the meanings.
 *
 * <p>
 * Actions: <b>Apply</b> sends the editable values to {@link SettingsPort#apply} (confirming first
 * when any REAPPLY field changed, #764) and lists the rejections without closing; <b>Restore</b>
 * resets the form to the current values; <b>New game with these values…</b> closes and returns
 * {@link Action.StartGame} so the client opens the New Game form prefilled; <b>Save as defaults</b>
 * confirms and writes {@code letrain.cfg}; <b>Close</b> (or Esc) returns {@link Action.Close}.
 */
final class SettingsDialog {

    /** Rows the tab bar takes. */
    static final int TAB_ROWS = 1;
    /** Rows the legend under the tabs takes. */
    static final int LEGEND_ROWS = 1;
    /** Blank row between the legend and the first field of every section (#754 convention). */
    static final int SECTION_TOP_ROWS = 1;
    /** Columns the state glyph and its trailing space take at the left of every row (#764). */
    static final int STATE_COLUMNS = 2;

    /** Where the values start: the state mark plus the page's own column (#764). */
    static int valueColumn(List<String> names) {
        return STATE_COLUMNS + NewGameDialog.valueColumn(names);
    }

    /** {@code Name:} padded so the value lands on {@code column}; the glyph takes the prefix. */
    static String alignedLabel(String name, int column) {
        return NewGameDialog.alignedLabel(name, column - STATE_COLUMNS);
    }

    /** Field states the dialog shows: the catalogue's apply state plus the scenario lock (#764). */
    enum FieldState {
        LIVE("●", TextColor.ANSI.GREEN, ""), REAPPLY("●", TextColor.ANSI.YELLOW, ""), NEW_GAME("●",
                TextColor.ANSI.RED, SettingsApplyResult.REQUIRES_NEW_GAME), SCENARIO("●",
                        TextColor.ANSI.WHITE, SettingsApplyResult.DEFINED_BY_SCENARIO);

        private final String glyph;
        private final TextColor color;
        private final String reason;

        FieldState(String glyph, TextColor color, String reason) {
            this.glyph = glyph;
            this.color = color;
            this.reason = reason;
        }

        String glyph() {
            return glyph;
        }

        TextColor color() {
            return color;
        }

        /** Reason appended to the row while the field is disabled ({@code ""} when editable). */
        String reason() {
            return reason;
        }

        boolean editable() {
            return this == LIVE || this == REAPPLY;
        }
    }

    /** Apply state shown for one field: the scenario lock wins over the catalogue state (D10). */
    static FieldState fieldState(SettingDefinition definition, Set<String> scenarioKeys) {
        if (scenarioKeys.contains(definition.key())) {
            return FieldState.SCENARIO;
        }
        return definition.applyState() == SettingApplyState.REAPPLY ? FieldState.REAPPLY
                : definition.applyState() == SettingApplyState.NEW_GAME ? FieldState.NEW_GAME
                        : FieldState.LIVE;
    }

    /** One footer action: its id, the label and the Alt mnemonic (#764). */
    record ActionButton(String id, String label, char mnemonic) {
    }

    static final String APPLY_ID = "apply";
    static final String RESTORE_ID = "restore";
    static final String NEW_GAME_ID = "newGame";
    static final String SAVE_DEFAULTS_ID = "saveDefaults";
    static final String CLOSE_ID = "close";

    /** The five actions, in display order. */
    static final List<ActionButton> ACTIONS =
            List.of(new ActionButton(APPLY_ID, SettingsText.APPLY, 'A'),
                    new ActionButton(RESTORE_ID, SettingsText.RESTORE, 'R'),
                    new ActionButton(NEW_GAME_ID, SettingsText.NEW_GAME, 'N'),
                    new ActionButton(SAVE_DEFAULTS_ID, SettingsText.SAVE_DEFAULTS, 'S'),
                    new ActionButton(CLOSE_ID, SettingsText.CLOSE, 'C'));

    /**
     * Footer rows for a terminal width, order kept: one row when the five mnemonic buttons fit, two
     * when they do not (70 columns, #764). Pure maths so the 70x20 layout is pinned by tests.
     */
    static List<List<ActionButton>> footerRows(int columns) {
        List<List<ActionButton>> rows = new ArrayList<>();
        List<ActionButton> row = new ArrayList<>();
        int width = 0;
        for (ActionButton button : ACTIONS) {
            int cell = button.label().length() + 4; // "< " + label + " >"
            int needed = row.isEmpty() ? cell : width + 1 + cell;
            if (!row.isEmpty() && needed > columns) {
                rows.add(List.copyOf(row));
                row.clear();
                width = cell;
            } else {
                width = needed;
            }
            row.add(button);
        }
        if (!row.isEmpty()) {
            rows.add(List.copyOf(row));
        }
        return rows;
    }

    /** REAPPLY fields (yellow) whose text differs from the current values: Apply confirms first. */
    static List<String> changedReapplyKeys(Map<String, String> baseline, Map<String, String> form) {
        List<String> changed = new ArrayList<>();
        for (SettingDefinition definition : SettingsCatalog.definitions()) {
            if (definition.applyState() != SettingApplyState.REAPPLY
                    || !form.containsKey(definition.key())) {
                continue;
            }
            String before = baseline.getOrDefault(definition.key(), "").trim();
            String after = form.get(definition.key()).trim();
            if (!before.equals(after)) {
                changed.add(definition.key());
            }
        }
        return changed;
    }

    /** What the player chose when the dialog closed. */
    sealed interface Action permits Action.Close, Action.StartGame {
        record Close() implements Action {
        }

        record StartGame(Map<String, String> settings) implements Action {
        }
    }

    /** Port the dialog uses to reach the running game; the presenter implements it. */
    interface SettingsPort {
        /** Applies the editable values and reports the rejections (S3a). */
        SettingsApplyResult apply(Map<String, String> values);

        /** Writes the values as {@code letrain.cfg} defaults; {@code null} on success. */
        String saveDefaults(Map<String, String> values);
    }

    /**
     * Opens the modal dialog and returns the chosen action. {@code currentSettings} are the running
     * game's effective values (the Restore baseline); {@code scenarioKeys} marks scenario-locked
     * fields.
     */
    static Action show(Screen screen, Map<String, String> currentSettings, Set<String> scenarioKeys,
            SettingsPort port) {
        Map<String, String> baseline = baselineOf(currentSettings);
        MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);
        gui.setTheme(NewGameDialog.darkTheme(gui.getTheme()));
        BasicWindow window = new BasicWindow();
        window.setHints(List.of(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS));
        window.setCloseWindowWithEscape(true);

        final Action[] result = {new Action.Close()};
        final Map<String, Supplier<String>> readers = new LinkedHashMap<>();
        final Map<String, Consumer<String>> writers = new LinkedHashMap<>();
        final Map<String, Boolean> editable = new LinkedHashMap<>();

        Panel root = new Panel(new BorderLayout());
        Panel content = new Panel(new BorderLayout());

        final List<SettingCategory> tabs = List.of(SettingCategory.TERRAIN, SettingCategory.WORLD,
                SettingCategory.ECONOMY, SettingCategory.CONSTRUCTION, SettingCategory.UI);
        Map<SettingCategory, Panel> sections = new EnumMap<>(SettingCategory.class);
        for (SettingCategory category : tabs) {
            sections.put(category,
                    buildSection(category, baseline, scenarioKeys, readers, writers, editable));
        }

        Panel tabBar = new Panel(new LinearLayout(Direction.HORIZONTAL));
        final SettingCategory[] active = {SettingCategory.TERRAIN};
        for (SettingCategory category : tabs) {
            // Same convention as the New Game dialog: tabs stay out of the Tab cycle (#757).
            Button button = new Button(category.displayName()) {
                @Override
                public boolean isFocusable() {
                    return false;
                }
            };
            button.setRenderer(NewGameDialog.tabRenderer(category, active));
            button.addListener(triggered -> {
                active[0] = category;
                showSection(content, sections, category, window);
            });
            tabBar.addComponent(button);
        }
        Panel top = new Panel(new LinearLayout(Direction.VERTICAL));
        top.addComponent(tabBar);
        top.addComponent(legendPanel());

        Label feedback = new Label("");

        Supplier<Map<String, String>> editableValues = () -> collect(readers, editable, true);
        Supplier<Map<String, String>> allValues = () -> collect(readers, editable, false);

        Runnable applyAction = () -> {
            Map<String, String> values = editableValues.get();
            List<String> reapply = changedReapplyKeys(baseline, values);
            if (!reapply.isEmpty() && !confirm(gui, SettingsText.REAPPLY_TITLE,
                    SettingsText.reapplyMessage(reapply))) {
                return;
            }
            SettingsApplyResult outcome = port.apply(values);
            if (outcome.hasRejections()) {
                feedback.setText("");
                message(gui, SettingsText.REJECTED_TITLE,
                        SettingsText.rejectedMessage(outcome.rejections()));
            } else {
                baseline.putAll(values);
                feedback.setText(SettingsText.APPLIED);
            }
            window.invalidate();
        };
        Runnable restoreAction = () -> {
            writers.forEach((key, writer) -> writer.accept(baseline.get(key)));
            feedback.setText(SettingsText.RESTORED);
            window.invalidate();
        };
        Runnable newGameAction = () -> {
            result[0] = new Action.StartGame(allValues.get());
            window.close();
        };
        Runnable saveDefaultsAction = () -> {
            if (!confirm(gui, SettingsText.SAVE_DEFAULTS_TITLE,
                    SettingsText.SAVE_DEFAULTS_CONFIRM)) {
                return;
            }
            String error = port.saveDefaults(allValues.get());
            feedback.setText(error == null ? SettingsText.SAVED_DEFAULTS
                    : SettingsText.SAVE_DEFAULTS_FAILED + error);
            window.invalidate();
        };
        Runnable closeAction = window::close;
        Map<String, Runnable> actions = new LinkedHashMap<>();
        actions.put(APPLY_ID, applyAction);
        actions.put(RESTORE_ID, restoreAction);
        actions.put(NEW_GAME_ID, newGameAction);
        actions.put(SAVE_DEFAULTS_ID, saveDefaultsAction);
        actions.put(CLOSE_ID, closeAction);

        Panel footer = new Panel(new LinearLayout(Direction.VERTICAL));
        for (List<ActionButton> rowButtons : footerRows(screen.getTerminalSize().getColumns())) {
            Panel row = new Panel(new LinearLayout(Direction.HORIZONTAL));
            for (ActionButton action : rowButtons) {
                row.addComponent(NewGameDialog.mnemonicButton(action.label(), action.mnemonic(),
                        actions.get(action.id())));
            }
            footer.addComponent(row);
        }
        footer.addComponent(feedback);

        window.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onInput(Window w, KeyStroke keyStroke, AtomicBoolean deliverEvent) {
                if (keyStroke.isCtrlDown() && (keyStroke.getKeyType() == KeyType.Tab
                        || keyStroke.getKeyType() == KeyType.ReverseTab)) {
                    active[0] = NewGameDialog.cycleTab(active[0], tabs,
                            keyStroke.getKeyType() == KeyType.ReverseTab);
                    showSection(content, sections, active[0], window);
                    deliverEvent.set(false);
                    return;
                }
                if (!keyStroke.isAltDown() || keyStroke.getCharacter() == null) {
                    return;
                }
                char key = Character.toLowerCase(keyStroke.getCharacter());
                Optional<SettingCategory> tab = NewGameDialog.tabHotkey(key, tabs);
                if (tab.isPresent()) {
                    active[0] = tab.get();
                    showSection(content, sections, active[0], window);
                    deliverEvent.set(false);
                    return;
                }
                for (ActionButton action : ACTIONS) {
                    if (Character.toLowerCase(action.mnemonic()) == key) {
                        actions.get(action.id()).run();
                        deliverEvent.set(false);
                        return;
                    }
                }
            }
        });

        root.addComponent(top, BorderLayout.Location.TOP);
        root.addComponent(content, BorderLayout.Location.CENTER);
        root.addComponent(footer, BorderLayout.Location.BOTTOM);

        window.setComponent(root);
        showSection(content, sections, active[0], window);
        gui.addWindowAndWait(window);
        return result[0];
    }

    /** Effective values overlaid on the catalogue defaults, so every field has text. */
    private static Map<String, String> baselineOf(Map<String, String> currentSettings) {
        Map<String, String> baseline = new LinkedHashMap<>();
        for (SettingDefinition definition : SettingsCatalog.definitions()) {
            String current = currentSettings == null ? null : currentSettings.get(definition.key());
            baseline.put(definition.key(), current != null ? current : definition.defaultValue());
        }
        return baseline;
    }

    /** Reads the widgets into a map: editable keys only, or every key for the other actions. */
    static Map<String, String> collect(Map<String, Supplier<String>> readers,
            Map<String, Boolean> editable, boolean onlyEditable) {
        Map<String, String> values = new LinkedHashMap<>();
        readers.forEach((key, reader) -> {
            if (!onlyEditable || Boolean.TRUE.equals(editable.get(key))) {
                values.put(key, reader.get());
            }
        });
        return values;
    }

    /** Legend under the tabs: the four state marks with their meaning (#764). */
    private static Panel legendPanel() {
        Panel legend = new Panel(NewGameDialog.rowLayout());
        addLegend(legend, FieldState.LIVE, SettingsText.LEGEND_LIVE);
        addLegend(legend, FieldState.REAPPLY, SettingsText.LEGEND_REAPPLY);
        addLegend(legend, FieldState.NEW_GAME, SettingsText.LEGEND_NEW_GAME);
        addLegend(legend, FieldState.SCENARIO, SettingsText.LEGEND_SCENARIO);
        return legend;
    }

    private static void addLegend(Panel legend, FieldState state, String text) {
        Label glyph = new Label(state.glyph());
        glyph.setForegroundColor(state.color());
        legend.addComponent(glyph);
        legend.addComponent(new Label(" " + text + "  "));
    }

    /** Swaps the active section: blank row, left margin and focus on its first field (#757). */
    private static void showSection(Panel content, Map<SettingCategory, Panel> sections,
            SettingCategory active, BasicWindow window) {
        content.removeAllComponents();
        content.addComponent(new EmptySpace(new TerminalSize(1, SECTION_TOP_ROWS)),
                BorderLayout.Location.TOP);
        content.addComponent(new EmptySpace(new TerminalSize(NewGameDialog.LEFT_MARGIN_COLUMNS, 1)),
                BorderLayout.Location.LEFT);
        Panel section = sections.get(active);
        content.addComponent(section, BorderLayout.Location.CENTER);
        if (window.getComponent() != null) {
            window.setFocusedInteractable(section.nextFocus(null));
        }
        window.invalidate();
    }

    /** One catalogue page: {@code ● Name: value} rows with their state marks (#764). */
    private static NewGameDialog.ScrollColumn buildSection(SettingCategory category,
            Map<String, String> baseline, Set<String> scenarioKeys,
            Map<String, Supplier<String>> readers, Map<String, Consumer<String>> writers,
            Map<String, Boolean> editable) {
        NewGameDialog.ScrollColumn columnPanel = new NewGameDialog.ScrollColumn();
        int column = valueColumn(NewGameDialog.fieldNames(category));
        for (SettingDefinition definition : SettingsCatalog.definitions()) {
            if (definition.category() == category) {
                columnPanel.addComponent(fieldRow(definition, baseline.get(definition.key()),
                        scenarioKeys, readers, writers, editable, column));
            }
        }
        return columnPanel;
    }

    /** One field row: state glyph, {@code Name:}, widget (+ unit) and the lock reason if any. */
    private static Panel fieldRow(SettingDefinition definition, String value,
            Set<String> scenarioKeys, Map<String, Supplier<String>> readers,
            Map<String, Consumer<String>> writers, Map<String, Boolean> editable, int column) {
        FieldState state = fieldState(definition, scenarioKeys);
        Panel row = new Panel(new LinearLayout(Direction.VERTICAL));
        Panel line = new Panel(NewGameDialog.rowLayout());
        Label glyph = new Label(state.glyph() + " ");
        glyph.setForegroundColor(state.color());
        line.addComponent(glyph);
        line.addComponent(new Label(alignedLabel(definition.displayName(), column)));
        if (definition.type() == SettingValueType.BOOLEAN) {
            CheckBox checkBox = NewGameDialog.scrollingCheckBox(Boolean.parseBoolean(value));
            checkBox.setEnabled(state.editable());
            line.addComponent(checkBox);
            readers.put(definition.key(), () -> checkBox.isChecked() ? "true" : "false");
            if (state.editable()) {
                writers.put(definition.key(),
                        text -> checkBox.setChecked(Boolean.parseBoolean(text)));
                editable.put(definition.key(), true);
            }
        } else {
            NewGameDialog.ValidatingTextBox textBox =
                    new NewGameDialog.ValidatingTextBox(definition, value, column);
            textBox.setEnabled(state.editable());
            line.addComponent(textBox);
            String unit = NewGameDialog.fieldUnit(definition);
            if (!unit.isEmpty()) {
                line.addComponent(new Label(" " + unit));
            }
            readers.put(definition.key(), textBox::getText);
            if (state.editable()) {
                writers.put(definition.key(), textBox::setText);
                editable.put(definition.key(), true);
            }
            textBox.setTooltipHost(row);
        }
        if (!state.editable()) {
            Label reason = new Label("  (" + state.reason() + ")");
            reason.setForegroundColor(TerminalView.MENU_DISABLED_FG_COLOR);
            line.addComponent(reason);
        }
        row.addComponent(line);
        return row;
    }

    private static boolean confirm(MultiWindowTextGUI gui, String title, String message) {
        return MessageDialogButton.Yes == new MessageDialogBuilder().setTitle(title)
                .setText(message).addButton(MessageDialogButton.Yes)
                .addButton(MessageDialogButton.No).build().showDialog(gui);
    }

    private static void message(MultiWindowTextGUI gui, String title, String message) {
        new MessageDialogBuilder().setTitle(title).setText(message)
                .addButton(MessageDialogButton.OK).build().showDialog(gui);
    }

    private SettingsDialog() {}
}
