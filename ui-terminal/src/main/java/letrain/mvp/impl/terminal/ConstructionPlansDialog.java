package letrain.mvp.impl.terminal;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TerminalTextUtils;
import com.googlecode.lanterna.graphics.ThemeDefinition;
import com.googlecode.lanterna.gui2.AbstractListBox;
import com.googlecode.lanterna.gui2.ActionListBox;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowListenerAdapter;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modal list of planned works (construction roadmap, phase 1). It is the 2D counterpart of the
 * future planned-works window: a compact table over the frozen game frame with the planner data the
 * author needs to compare routes, a total line and the actions Add/Edit/Quotes/Delete.
 *
 * <p>
 * The dialog is deliberately presentation only. The presenter builds every {@link Row} with the
 * cost and time already formatted ({@link RoutePreviewStatus#formatCost(double)} and
 * {@link RoutePreviewStatus#formatTicks(long)}), and reacts to the returned {@link Result}; it owns
 * the list itself. {@code Add} closes the dialog and returns to the presenter, which starts the
 * "save route" planning flow and decides when to reopen the list.
 *
 * <p>
 * <b>Run stays disabled</b> in phase 1: there are no construction companies or reserved cash yet,
 * so the button is shown (so the action is discoverable) but cannot be activated and has no
 * shortcut.
 *
 * <p>
 * The table formatting lives in pure static methods ({@link #header()}, {@link #formatRow(Row)},
 * {@link #totalLine(List)}, {@link #clampIndex(int, int)}) so it is pinned without a terminal.
 */
final class ConstructionPlansDialog {

    /** What the player chose when the dialog closed. */
    enum Action {
        CLOSE, ADD, EDIT, QUOTES, DELETE
    }

    /**
     * One table row. {@code id} is the plan identity shown in the {@code #} column; the other
     * fields are display text, so the presenter keeps all pricing/time rules. {@code warning} is
     * empty when the plan is fine, otherwise it is shown as a trailing {@code !}.
     */
    record Row(int id, String state, String from, String to, String policy, String company,
            String quality, int cells, String cost, String time, String warning) {
    }

    /** Chosen action plus the selected row index ({@code -1} when the list is empty). */
    record Result(Action action, int selectedIndex) {
    }

    /** Table header, with every column starting on the same cell as {@link #formatRow(Row)}. */
    static final String HEADER =
            "#  State    From   To      Policy     Company  Quality Cells  Cost       Time";

    private static final Pattern HOURS_PATTERN = Pattern.compile("(\\d+)\\s*h");
    private static final Pattern MINUTES_PATTERN = Pattern.compile("(\\d+)\\s*min");

    private ConstructionPlansDialog() {}

    /** Opens the modal dialog and returns the chosen action and the selected row index. */
    static Result show(Screen screen, List<Row> rows, int selectedIndex) {
        return show(screen, rows, selectedIndex, null);
    }

    /**
     * Opens the modal dialog with an optional total line computed by the presenter. The presenter
     * owns the exact plan costs/ticks; when {@code total} is null the dialog falls back to the
     * display rows (used by focused UI tests).
     */
    static Result show(Screen screen, List<Row> rows, int selectedIndex, String total) {
        List<Row> safeRows = rows == null ? List.of() : List.copyOf(rows);
        MultiWindowTextGUI gui = DialogWindow.programGui(screen);
        TerminalSize box = DialogWindow.dialogSize(screen.getTerminalSize(), 86, 110);

        BasicWindow window = new BasicWindow("Planning");
        window.setHints(List.of(Window.Hint.CENTERED));
        window.setFixedSize(box);
        window.setCloseWindowWithEscape(true);

        final Result[] result =
                {new Result(Action.CLOSE, clampIndex(selectedIndex, safeRows.size()))};

        Panel root = new Panel(new BorderLayout());
        Panel body = new Panel(new BorderLayout());
        Panel heading = new Panel(new LinearLayout(Direction.VERTICAL));
        Label header = new Label(HEADER);
        heading.addComponent(header);
        heading.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        body.addComponent(heading, BorderLayout.Location.TOP);

        ActionListBox list = new ActionListBox() {
            @Override
            public boolean isFocusable() {
                return !safeRows.isEmpty();
            }
        };
        for (Row row : safeRows) {
            list.addItem(formatRow(row), () -> {
                // The phase-1 list only selects rows; details are deliberately out of scope.
            });
        }
        if (!safeRows.isEmpty()) {
            list.setSelectedIndex(clampIndex(selectedIndex, safeRows.size()));
        }
        list.setListItemRenderer(rowRenderer(safeRows));

        if (safeRows.isEmpty()) {
            body.addComponent(new Label(emptyMessage()), BorderLayout.Location.CENTER);
        } else {
            body.addComponent(list, BorderLayout.Location.CENTER);
        }

        Panel footer = new Panel(new LinearLayout(Direction.VERTICAL));
        footer.addComponent(new Label(total == null ? totalLine(safeRows) : total));
        footer.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        Runnable close = () -> {
            result[0] = new Result(Action.CLOSE, selectedIndex(list));
            window.close();
        };
        Runnable add = () -> closeWith(window, result, Action.ADD, list);
        Runnable edit = () -> closeWith(window, result, Action.EDIT, list);
        Runnable quotes = () -> closeWith(window, result, Action.QUOTES, list);
        Runnable delete = () -> closeWith(window, result, Action.DELETE, list);

        Panel actionRow = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Button addButton = NewGameDialog.mnemonicButton("Add", 'A', add);
        Button editButton = NewGameDialog.mnemonicButton("Edit", 'E', edit);
        Button quotesButton = NewGameDialog.mnemonicButton("Quotes", 'Q', quotes);
        Button deleteButton = NewGameDialog.mnemonicButton("Delete", 'D', delete);
        actionRow.addComponent(addButton);
        actionRow.addComponent(editButton);
        actionRow.addComponent(quotesButton);
        actionRow.addComponent(deleteButton);

        boolean hasSelection = !safeRows.isEmpty();
        editButton.setEnabled(hasSelection);
        quotesButton.setEnabled(hasSelection);
        deleteButton.setEnabled(hasSelection);

        Panel runRow = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Button runButton = new Button("Run");
        runButton.setEnabled(false);
        runRow.addComponent(runButton);
        runRow.addComponent(new Label("  (available in a later phase)"));

        Panel closeRow = new Panel(new LinearLayout(Direction.HORIZONTAL));
        closeRow.addComponent(NewGameDialog.mnemonicButton("Close", 'C', close));

        footer.addComponent(actionRow);
        footer.addComponent(runRow);
        footer.addComponent(closeRow);

        window.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onInput(Window w, KeyStroke keyStroke, AtomicBoolean deliverEvent) {
                if (keyStroke.isCtrlDown()) {
                    return;
                }
                Character character = keyStroke.getCharacter();
                if (keyStroke.isAltDown()) {
                    if (character == null) {
                        return;
                    }
                    Action action = shortcutAction(character, hasSelection);
                    if (action != null) {
                        switch (action) {
                            case ADD -> add.run();
                            case EDIT -> edit.run();
                            case QUOTES -> quotes.run();
                            case DELETE -> delete.run();
                            case CLOSE -> close.run();
                        }
                        deliverEvent.set(false);
                    }
                    return;
                }
                if (keyStroke.getKeyType() == KeyType.Escape) {
                    close.run();
                    deliverEvent.set(false);
                    return;
                }
                if (character == null) {
                    return;
                }
                switch (Character.toLowerCase(character)) {
                    case 'a' -> {
                        add.run();
                        deliverEvent.set(false);
                    }
                    case 'e' -> {
                        if (hasSelection) {
                            edit.run();
                        }
                        deliverEvent.set(false);
                    }
                    case 'q' -> {
                        if (hasSelection) {
                            quotes.run();
                        }
                        deliverEvent.set(false);
                    }
                    case 'd' -> {
                        if (hasSelection) {
                            delete.run();
                        }
                        deliverEvent.set(false);
                    }
                    default -> {
                        // No other shortcut: let the focused component handle the key.
                    }
                }
            }
        });

        root.addComponent(body, BorderLayout.Location.CENTER);
        root.addComponent(footer, BorderLayout.Location.BOTTOM);
        window.setComponent(root);
        if (hasSelection) {
            window.setFocusedInteractable(list);
        } else {
            window.setFocusedInteractable(addButton);
        }
        gui.addWindowAndWait(window);
        return result[0];
    }

    private static void closeWith(BasicWindow window, Result[] result, Action action,
            ActionListBox list) {
        result[0] = new Result(action, selectedIndex(list));
        window.close();
    }

    private static int selectedIndex(ActionListBox list) {
        return Math.max(-1, list.getSelectedIndex());
    }

    /** Clamps the remembered selection to an existing row; {@code -1} when there are no rows. */
    static int clampIndex(int index, int rowCount) {
        if (rowCount <= 0) {
            return -1;
        }
        return Math.max(0, Math.min(index, rowCount - 1));
    }

    /**
     * Action for an Alt mnemonic. Add and Close are always available; Edit, Quotes and Delete need
     * a selected row. {@code Alt+R} deliberately maps to nothing because Run is disabled in phase
     * 1.
     */
    static Action shortcutAction(char key, boolean hasSelection) {
        return switch (Character.toLowerCase(key)) {
            case 'a' -> Action.ADD;
            case 'e' -> hasSelection ? Action.EDIT : null;
            case 'q' -> hasSelection ? Action.QUOTES : null;
            case 'd' -> hasSelection ? Action.DELETE : null;
            case 'c' -> Action.CLOSE;
            default -> null;
        };
    }

    /** Table header, kept in one place so the row formatter and the label cannot drift. */
    static String header() {
        return HEADER;
    }

    /**
     * One fixed-width table line. Values that would overflow their column are truncated so the
     * table never wraps or pushes the following columns; a non-empty warning is marked with a
     * trailing {@code !}.
     */
    static String formatRow(Row row) {
        StringBuilder line = new StringBuilder();
        line.append(fitLeft(Integer.toString(row.id()), 2));
        line.append(" ");
        line.append(fitLeft(row.state(), 9));
        line.append(fitLeft(row.from(), 7));
        line.append(fitLeft(row.to(), 8));
        line.append(fitLeft(row.policy(), 11));
        line.append(fitLeft(row.company(), 9));
        line.append(fitLeft(row.quality(), 8));
        line.append(fitRight(Integer.toString(row.cells()), 7));
        line.append(fitLeft(row.cost(), 11));
        line.append(row.time() == null ? "" : row.time());
        if (row.warning() != null && !row.warning().isBlank()) {
            line.append(" !");
        }
        return line.toString();
    }

    /**
     * Row renderer that keeps the selected row highlighted even when focus moves to a footer
     * button. The default Lanterna list renderer only paints the selected background while the list
     * itself has focus, which made Delete ambiguous.
     */
    static AbstractListBox.ListItemRenderer<Runnable, ActionListBox> rowRenderer(List<Row> rows) {
        return new AbstractListBox.ListItemRenderer<>() {
            @Override
            public String getLabel(ActionListBox listBox, int index, Runnable item) {
                return index >= 0 && index < rows.size() ? formatRow(rows.get(index)) : "";
            }

            @Override
            public void drawItem(TextGUIGraphics graphics, ActionListBox listBox, int index,
                    Runnable item, boolean selected, boolean focused) {
                ThemeDefinition definition =
                        listBox.getTheme().getDefinition(AbstractListBox.class);
                graphics.applyThemeStyle(
                        selected ? definition.getSelected() : definition.getNormal());
                String label = getLabel(listBox, index, item);
                label = TerminalTextUtils.fitString(label, graphics.getSize().getColumns());
                while (TerminalTextUtils.getColumnWidth(label) < graphics.getSize().getColumns()) {
                    label += " ";
                }
                graphics.putString(0, 0, label);
            }
        };
    }

    /** One-row state message shown instead of the table when the list is empty. */
    static String emptyMessage() {
        return "No planned works yet. Press [A] Add to plan a route.";
    }

    /**
     * Total line from already-formatted figures: {@code Total: 3 works · $385.800 · 17 min}. Kept
     * pure so the presenter can reuse it later, and so singular/plural is pinned by a test.
     */
    static String totalLine(long workCount, String cost, String time) {
        return "Total: " + workCount + (workCount == 1 ? " work" : " works") + " · " + cost + " · "
                + time;
    }

    /**
     * Total for the current rows, used when the presenter does not provide an exact total. The
     * modal path normally passes the exact total computed from the plans; this fallback aggregates
     * the display strings so focused UI tests remain self-contained.
     */
    static String totalLine(List<Row> rows) {
        long cost = 0;
        long minutes = 0;
        if (rows != null) {
            for (Row row : rows) {
                cost += parseCost(row.cost());
                minutes += parseMinutes(row.time());
            }
        }
        return totalLine(rows == null ? 0 : rows.size(), RoutePreviewStatus.formatCost(cost),
                RoutePreviewStatus.formatTicks(minutes * RoutePreviewStatus.TICKS_PER_GAME_MINUTE));
    }

    /** Dollars read back from {@code $1.234.500} (or {@code -$...}); 0 when unparseable. */
    static long parseCost(String formatted) {
        if (formatted == null) {
            return 0;
        }
        String digits = formatted.replace("$", "").replace(".", "").replace(",", "").trim();
        if (digits.isEmpty() || "-".equals(digits)) {
            return 0;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Minutes read back from {@code 12 min}, {@code 1 h} or {@code 1 h 30 min}. */
    static long parseMinutes(String formatted) {
        if (formatted == null) {
            return 0;
        }
        long minutes = 0;
        Matcher hours = HOURS_PATTERN.matcher(formatted);
        if (hours.find()) {
            minutes += Long.parseLong(hours.group(1)) * 60;
        }
        Matcher mins = MINUTES_PATTERN.matcher(formatted);
        if (mins.find()) {
            minutes += Long.parseLong(mins.group(1));
        }
        return minutes;
    }

    private static String fitLeft(String text, int width) {
        String value = text == null ? "" : text;
        if (value.length() > width) {
            value = value.substring(0, width);
        }
        return value + " ".repeat(width - value.length());
    }

    private static String fitRight(String text, int width) {
        String value = text == null ? "" : text;
        if (value.length() > width) {
            value = value.substring(value.length() - width);
        }
        return " ".repeat(width - value.length()) + value;
    }
}
