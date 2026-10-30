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

    static final String HEADER = "Company   Cost       Time    Quality Capabilities";

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
        heading.addComponent(new Label(HEADER));
        heading.addComponent(new EmptySpace(new TerminalSize(1, 1)));
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
                if (character != null && Character.toLowerCase(character) == 's') {
                    select.run();
                    deliverEvent.set(false);
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
        String company = fitLeft(quote.company().name(), 10);
        String cost =
                quote.feasible() ? fitLeft(RoutePreviewStatus.formatCost(quote.totalCost()), 11)
                        : fitLeft("-", 11);
        String time =
                quote.feasible() ? fitLeft(RoutePreviewStatus.formatTicks(quote.totalTicks()), 8)
                        : fitLeft("-", 8);
        String quality =
                fitLeft(quote.quality() == ConstructionCompany.Quality.LOW ? "Low" : "High", 8);
        String capabilities = capabilityLabel(quote);
        String line = company + cost + time + quality + capabilities;
        return quote.feasible() ? line : line + " !";
    }

    private static String capabilityLabel(WorkQuote quote) {
        boolean bridge = quote.requiredCapabilities().contains(Capability.BRIDGE);
        boolean tunnel = quote.requiredCapabilities().contains(Capability.TUNNEL);
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
