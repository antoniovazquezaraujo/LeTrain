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

/**
 * Modal list of named marks (bookmarks) for the 2D client.
 *
 * <p>
 * Marks are model-owned positions created by {@code mark <name>;} and by the planned-works Add
 * flow. This dialog is a management view: it lists names and positions, can move the cursor to a
 * mark, and can delete one. Creating marks stays in the console and in the works flow for now.
 *
 * <p>
 * It uses the same default palette and frozen game backdrop as the program editor and the Works
 * dialog, and keeps the selected row highlighted when focus moves to the footer buttons.
 */
final class MarksDialog {

    /** What the player chose when the dialog closed. */
    enum Action {
        GO, DELETE, CLOSE
    }

    /** One mark row; the name and position are display-ready. */
    record Row(String name, String position) {
    }

    /** Chosen action plus the selected row index ({@code -1} when there are no marks). */
    record Result(Action action, int selectedIndex) {
    }

    /** Table header; both rows and header use the same fixed columns. */
    static final String HEADER = "Mark                    Position";

    private MarksDialog() {}

    /** Opens the modal list and returns the chosen action and row index. */
    static Result show(Screen screen, List<Row> rows, int selectedIndex) {
        List<Row> safeRows = rows == null ? List.of() : List.copyOf(rows);
        MultiWindowTextGUI gui = DialogWindow.programGui(screen);
        TerminalSize box = DialogWindow.dialogSize(screen.getTerminalSize());

        BasicWindow window = new BasicWindow("Marks");
        window.setHints(List.of(Window.Hint.CENTERED));
        window.setFixedSize(box);
        window.setCloseWindowWithEscape(true);

        final Result[] result =
                {new Result(Action.CLOSE, clampIndex(selectedIndex, safeRows.size()))};
        boolean hasSelection = !safeRows.isEmpty();

        Panel root = new Panel(new BorderLayout());
        Panel body = new Panel(new BorderLayout());
        Panel heading = new Panel(new LinearLayout(Direction.VERTICAL));
        heading.addComponent(new Label(HEADER));
        heading.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        body.addComponent(heading, BorderLayout.Location.TOP);

        ActionListBox list = new ActionListBox() {
            @Override
            public boolean isFocusable() {
                return hasSelection;
            }
        };
        for (Row row : safeRows) {
            list.addItem(formatRow(row), () -> {
                // Selection is the action target; Enter is handled by the dialog listener.
            });
        }
        if (hasSelection) {
            list.setSelectedIndex(clampIndex(selectedIndex, safeRows.size()));
        }
        list.setListItemRenderer(rowRenderer(safeRows));

        if (hasSelection) {
            body.addComponent(list, BorderLayout.Location.CENTER);
        } else {
            body.addComponent(new Label(emptyMessage()), BorderLayout.Location.CENTER);
        }

        Panel footer = new Panel(new LinearLayout(Direction.VERTICAL));
        footer.addComponent(new Label(totalLine(safeRows.size())));
        footer.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        Runnable close = () -> {
            result[0] = new Result(Action.CLOSE, selectedIndex(list));
            window.close();
        };
        Runnable go = () -> closeWith(window, result, Action.GO, list);
        Runnable delete = () -> closeWith(window, result, Action.DELETE, list);

        Panel actions = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Button goButton = NewGameDialog.mnemonicButton("Go", 'G', go);
        Button deleteButton = NewGameDialog.mnemonicButton("Delete", 'D', delete);
        Button closeButton = NewGameDialog.mnemonicButton("Close", 'C', close);
        actions.addComponent(goButton);
        actions.addComponent(deleteButton);
        actions.addComponent(closeButton);
        goButton.setEnabled(hasSelection);
        deleteButton.setEnabled(hasSelection);
        footer.addComponent(actions);

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
                        run(action, go, delete, close);
                        deliverEvent.set(false);
                    }
                    return;
                }
                if (keyStroke.getKeyType() == KeyType.Escape) {
                    close.run();
                    deliverEvent.set(false);
                    return;
                }
                if (keyStroke.getKeyType() == KeyType.Enter) {
                    if (!(w.getFocusedInteractable() instanceof Button) && hasSelection) {
                        go.run();
                        deliverEvent.set(false);
                    }
                    return;
                }
                if (character == null) {
                    return;
                }
                switch (Character.toLowerCase(character)) {
                    case 'g' -> {
                        if (hasSelection) {
                            go.run();
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
                        // Let the focused component handle other keys.
                    }
                }
            }
        });

        root.addComponent(body, BorderLayout.Location.CENTER);
        root.addComponent(footer, BorderLayout.Location.BOTTOM);
        window.setComponent(root);
        window.setFocusedInteractable(hasSelection ? list : closeButton);
        gui.addWindowAndWait(window);
        return result[0];
    }

    private static void run(Action action, Runnable go, Runnable delete, Runnable close) {
        switch (action) {
            case GO -> go.run();
            case DELETE -> delete.run();
            case CLOSE -> close.run();
        }
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

    /** Action for an Alt mnemonic. Go/Delete need a selected mark; Close is always available. */
    static Action shortcutAction(char key, boolean hasSelection) {
        return switch (Character.toLowerCase(key)) {
            case 'g' -> hasSelection ? Action.GO : null;
            case 'd' -> hasSelection ? Action.DELETE : null;
            case 'c' -> Action.CLOSE;
            default -> null;
        };
    }

    /** One fixed-width table line. */
    static String formatRow(Row row) {
        String name = row.name() == null ? "" : row.name();
        String position = row.position() == null ? "" : row.position();
        if (name.length() > 24) {
            name = name.substring(0, 24);
        }
        if (position.length() > 12) {
            position = position.substring(0, 12);
        }
        return name + " ".repeat(24 - name.length()) + position
                + " ".repeat(12 - position.length());
    }

    static String emptyMessage() {
        return "No marks yet. Create one with the console: mark <name>;";
    }

    static String totalLine(int markCount) {
        return "Total: " + markCount + (markCount == 1 ? " mark" : " marks");
    }

    /**
     * Row renderer that keeps the selected mark highlighted when focus moves to Go/Delete/Close.
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
}
