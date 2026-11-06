package letrain.mvp.impl.terminal;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TerminalTextUtils;
import com.googlecode.lanterna.TextColor;
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
import letrain.construction.ConstructionCompany;
import letrain.construction.ConstructionCompany.Capability;
import letrain.construction.WorkQuote;

/**
 * Modal comparison of company quotes for one planned work. All catalogue companies are shown; a
 * company without the route's required capabilities is marked unavailable and cannot be selected.
 */
final class QuotesDialog {

    enum Action {
        SELECT, CLOSE
    }

    /** Chosen action and selected quote; {@code quote} is null when the dialog is cancelled. */
    record Result(Action action, int selectedIndex, WorkQuote quote) {
    }

    static final int COMPANY_WIDTH = 10;
    static final int COST_WIDTH = 11;
    static final int TIME_WIDTH = 8;
    static final int QUALITY_WIDTH = 8;
    static final int CAPABILITIES_WIDTH = 13;
    private static final char COLUMN_SEPARATOR = '|';

    static final String HEADER = buildHeader();

    private static String buildHeader() {
        StringBuilder line = new StringBuilder();
        appendCentered(line, "Company", COMPANY_WIDTH);
        appendSeparator(line);
        appendCentered(line, "Cost", COST_WIDTH);
        appendSeparator(line);
        appendCentered(line, "Time", TIME_WIDTH);
        appendSeparator(line);
        appendCentered(line, "Quality", QUALITY_WIDTH);
        appendSeparator(line);
        appendCentered(line, "Capabilities", CAPABILITIES_WIDTH);
        return line.toString();
    }

    private QuotesDialog() {}

    static Result show(Screen screen, List<WorkQuote> quotes, int selectedIndex) {
        List<WorkQuote> safeQuotes = quotes == null ? List.of() : List.copyOf(quotes);
        MultiWindowTextGUI gui = DialogWindow.programGui(screen);
        TerminalSize box = DialogWindow.dialogSize(screen.getTerminalSize());
        BasicWindow window = new BasicWindow("Quotes");
        window.setHints(List.of(Window.Hint.CENTERED));
        window.setFixedSize(box);
        window.setCloseWindowWithEscape(true);

        final Result[] result =
                {new Result(Action.CLOSE, clampIndex(selectedIndex, safeQuotes.size()), null)};
        boolean hasQuotes = !safeQuotes.isEmpty();

        Panel root = new Panel(new BorderLayout());
        Panel body = new Panel(new BorderLayout());
        Panel heading = new Panel(new LinearLayout(Direction.VERTICAL));
        String headerText = HEADER + " ".repeat(Math.max(0, box.getColumns() - HEADER.length()));
        Label header = new Label(headerText);
        header.setForegroundColor(TextColor.ANSI.BLACK);
        header.setBackgroundColor(TextColor.ANSI.CYAN);
        heading.addComponent(header);
        body.addComponent(heading, BorderLayout.Location.TOP);

        ActionListBox list = new ActionListBox() {
            @Override
            public boolean isFocusable() {
                return hasQuotes;
            }
        };
        for (WorkQuote quote : safeQuotes) {
            list.addItem(formatRow(quote), () -> {
                // Selection is the target; Enter is handled by the dialog listener.
            });
        }
        if (hasQuotes) {
            list.setSelectedIndex(clampIndex(selectedIndex, safeQuotes.size()));
        }
        list.setListItemRenderer(rowRenderer(safeQuotes));
        body.addComponent(hasQuotes ? list : new Label("No companies available."),
                BorderLayout.Location.CENTER);

        Panel footer = new Panel(new LinearLayout(Direction.VERTICAL));
        footer.addComponent(new Label(unavailableLine(safeQuotes)));
        footer.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        Runnable close = () -> {
            result[0] = new Result(Action.CLOSE, selectedIndex(list), null);
            window.close();
        };
        Runnable select = () -> {
            int index = selectedIndex(list);
            if (index >= 0 && index < safeQuotes.size() && safeQuotes.get(index).feasible()) {
                result[0] = new Result(Action.SELECT, index, safeQuotes.get(index));
                window.close();
            }
        };

        Panel actions = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Button selectButton = NewGameDialog.mnemonicButton("Select", 'S', select);
        Button cancelButton = NewGameDialog.mnemonicButton("Cancel", 'C', close);
        actions.addComponent(selectButton);
        actions.addComponent(cancelButton);
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
                    switch (Character.toLowerCase(character)) {
                        case 's' -> {
                            select.run();
                            deliverEvent.set(false);
                        }
                        case 'c' -> {
                            close.run();
                            deliverEvent.set(false);
                        }
                        default -> {
                            // No other Alt shortcut.
                        }
                    }
                    return;
                }
                if (keyStroke.getKeyType() == KeyType.Escape) {
                    close.run();
                    deliverEvent.set(false);
                    return;
                }
                if (keyStroke.getKeyType() == KeyType.Enter) {
                    if (!(w.getFocusedInteractable() instanceof Button)) {
                        select.run();
                        deliverEvent.set(false);
                    }
                    return;
                }
                if (character != null) {
                    switch (Character.toLowerCase(character)) {
                        case 'j' -> {
                            moveSelection(list, safeQuotes.size(), 1);
                            deliverEvent.set(false);
                        }
                        case 'k' -> {
                            moveSelection(list, safeQuotes.size(), -1);
                            deliverEvent.set(false);
                        }
                        case 's' -> {
                            select.run();
                            deliverEvent.set(false);
                        }
                        default -> {
                            // No other shortcut.
                        }
                    }
                }
            }
        });

        root.addComponent(body, BorderLayout.Location.CENTER);
        root.addComponent(footer, BorderLayout.Location.BOTTOM);
        window.setComponent(root);
        window.setFocusedInteractable(hasQuotes ? list : cancelButton);
        gui.addWindowAndWait(window);
        return result[0];
    }

    private static int selectedIndex(ActionListBox list) {
        return Math.max(-1, list.getSelectedIndex());
    }

    /** Moves the table selection with j/k, wrapping around the ends. */
    static void moveSelection(ActionListBox list, int rowCount, int delta) {
        if (rowCount <= 0) {
            return;
        }
        int current = list.getSelectedIndex();
        int next = current < 0 ? (delta > 0 ? 0 : rowCount - 1)
                : Math.floorMod(current + delta, rowCount);
        list.setSelectedIndex(next);
        list.takeFocus();
    }

    static int clampIndex(int index, int rowCount) {
        if (rowCount <= 0) {
            return -1;
        }
        return Math.max(0, Math.min(index, rowCount - 1));
    }

    /** Text shown below the table for companies that cannot quote this route. */
    static String unavailableLine(List<WorkQuote> quotes) {
        List<String> unavailable = quotes.stream().filter(quote -> !quote.feasible()).map(
                quote -> quote.company().name() + " (" + quote.reason().orElse("unavailable") + ")")
                .toList();
        return unavailable.isEmpty() ? "All companies available"
                : "Unavailable: " + String.join(" · ", unavailable);
    }

    static String formatRow(WorkQuote quote) {
        String company = fitLeft(quote.company().name(), COMPANY_WIDTH);
        String cost = quote.feasible()
                ? fitRight(RoutePreviewStatus.formatCost(quote.totalCost()), COST_WIDTH)
                : fitRight("-", COST_WIDTH);
        String time = quote.feasible()
                ? fitRight(RoutePreviewStatus.formatTicks(quote.totalTicks()), TIME_WIDTH)
                : fitRight("-", TIME_WIDTH);
        String quality = fitLeft(
                quote.quality() == ConstructionCompany.Quality.LOW ? "Low" : "High", QUALITY_WIDTH);
        String capabilities = fitLeft(capabilityLabel(quote), CAPABILITIES_WIDTH);
        StringBuilder line = new StringBuilder(company);
        appendSeparator(line);
        line.append(cost);
        appendSeparator(line);
        line.append(time);
        appendSeparator(line);
        line.append(quality);
        appendSeparator(line);
        line.append(capabilities);
        return quote.feasible() ? line.toString() : line + " !";
    }

    private static void appendSeparator(StringBuilder line) {
        line.append(' ').append(COLUMN_SEPARATOR).append(' ');
    }

    private static void appendCentered(StringBuilder line, String text, int width) {
        String value = text == null ? "" : text;
        if (value.length() > width) {
            value = value.substring(0, width);
        }
        int left = (width - value.length()) / 2;
        int right = width - value.length() - left;
        line.append(" ".repeat(left)).append(value).append(" ".repeat(right));
    }

    private static String fitRight(String text, int width) {
        String value = text == null ? "" : text;
        if (value.length() > width) {
            value = value.substring(0, width);
        }
        return " ".repeat(width - value.length()) + value;
    }

    private static String capabilityLabel(WorkQuote quote) {
        boolean bridge = quote.company().capabilities().contains(Capability.BRIDGE);
        boolean tunnel = quote.company().capabilities().contains(Capability.TUNNEL);
        if (bridge && tunnel) {
            return "Bridge+Tunnel";
        }
        if (bridge) {
            return "Bridge";
        }
        if (tunnel) {
            return "Tunnel";
        }
        return "-";
    }

    private static String fitLeft(String text, int width) {
        String value = text == null ? "" : text;
        if (value.length() > width) {
            value = value.substring(0, width);
        }
        return value + " ".repeat(width - value.length());
    }

    /** Keeps the selected quote visible when focus moves to Select/Cancel. */
    static AbstractListBox.ListItemRenderer<Runnable, ActionListBox> rowRenderer(
            List<WorkQuote> quotes) {
        return new AbstractListBox.ListItemRenderer<>() {
            @Override
            public String getLabel(ActionListBox listBox, int index, Runnable item) {
                return index >= 0 && index < quotes.size() ? formatRow(quotes.get(index)) : "";
            }

            @Override
            public void drawItem(TextGUIGraphics graphics, ActionListBox listBox, int index,
                    Runnable item, boolean selected, boolean focused) {
                ThemeDefinition definition =
                        listBox.getTheme().getDefinition(AbstractListBox.class);
                graphics.applyThemeStyle(
                        selected ? definition.getSelected() : definition.getNormal());
                String label = getLabel(listBox, index, item);
                if (index >= 0 && index < quotes.size() && !quotes.get(index).feasible()) {
                    graphics.setForegroundColor(TerminalView.MENU_DISABLED_FG_COLOR);
                }
                label = TerminalTextUtils.fitString(label, graphics.getSize().getColumns());
                while (TerminalTextUtils.getColumnWidth(label) < graphics.getSize().getColumns()) {
                    label += " ";
                }
                graphics.putString(0, 0, label);
            }
        };
    }
}
