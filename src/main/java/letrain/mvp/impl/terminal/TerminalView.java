package letrain.mvp.impl.terminal;

import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.TextColor.ANSI;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.gui2.ActionListBox;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.LocalizedString;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.dialogs.FileDialogBuilder;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import letrain.map.Page;
import letrain.map.Point;
import letrain.mvp.GameViewListener;
import letrain.mvp.Model.GameModeMenuOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modernized Terminal implementation of the View interface using Lanterna.
 * This class isolates terminal-specific logic and UI handling.
 */
public class TerminalView implements letrain.mvp.View {
    private static final Logger log = LoggerFactory.getLogger(TerminalView.class);
    private final GameViewListener gameViewListener;
    private Point scrollOffset = new Point(0, 0);
    private Screen screen;
    private DefaultTerminalFactory terminalFactory;
    private Terminal terminal;
    private TerminalSize terminalSize;
    private TextGraphics gameBox;
    private TerminalPosition gameBoxPosition;
    private TerminalSize gameBoxSize;
    private TextGraphics menuBox;
    private TerminalPosition menuBoxPosition;
    private TerminalSize menuBoxSize;

    private TextColor fgColor;
    private TextColor bgColor;
    boolean endOfGame = false;
    static final TextColor NORMAL_MENU_FG_COLOR = ANSI.WHITE;
    static final TextColor NORMAL_MENU_BG_COLOR = ANSI.BLACK;
    static final TextColor DISABLED_FG_COLOR = ANSI.BLACK_BRIGHT;
    static final TextColor SELECTED_BG_COLOR = ANSI.BLUE;
    static final TextColor SHORTCUT_COLOR = ANSI.GREEN_BRIGHT;

    public TerminalView(GameViewListener gameViewListener) {
        this.gameViewListener = gameViewListener;
        terminalFactory = new DefaultTerminalFactory();

        // Configure font for Swing terminal
        try {
            InputStream is = getClass().getResourceAsStream("/fonts/JuliaMono-Regular.ttf");
            if (is != null) {
                Font customFont = Font.createFont(Font.TRUETYPE_FONT, is).deriveFont(20f);
                terminalFactory.setTerminalEmulatorFontConfiguration(
                        com.googlecode.lanterna.terminal.swing.SwingTerminalFontConfiguration.newInstance(customFont));
            } else {
                log.warn("Font resource /fonts/JuliaMono-Regular.ttf not found in classpath!");
            }

        } catch (Exception e) {
            log.warn("Error loading custom JuliaMono font for terminal: {}", e.getMessage(), e);
        }


        try {
            terminal = terminalFactory.createTerminal();
            terminal.setCursorVisible(false);
            setScreen(createScreen(terminal));

            // Fix for "zombie process" when closing window via "X" button
            // If the terminal is a Swing frame, we add a listener to detect closure
            try {
                // We use reflection or check if it implements a known Swing interface
                // Lanterna Swing terminals typically have a component or are a JFrame
                Object t = terminal;
                if (t instanceof javax.swing.JFrame) {
                    ((javax.swing.JFrame) t).addWindowListener(new WindowAdapter() {
                        @Override
                        public void windowClosing(WindowEvent e) {
                            setEndOfGame(true);
                        }
                    });
                } else if (t.getClass().getMethod("getJFrame") != null) {
                    Object frame = t.getClass().getMethod("getJFrame").invoke(t);
                    if (frame instanceof javax.swing.JFrame) {
                        ((javax.swing.JFrame) frame).addWindowListener(new WindowAdapter() {
                            @Override
                            public void windowClosing(WindowEvent e) {
                                setEndOfGame(true);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                // If reflection fails or it's not Swing, we just continue
                log.debug("Terminal is not a Swing frame or doesn't expose JFrame", e);
            }
        } catch (IOException e) {
            log.error("Error creating terminal for TerminalView", e);
        }
        terminalSize = screen.getTerminalSize();
        gameBox = screen.newTextGraphics();
        menuBox = screen.newTextGraphics();
        this.fgColor = NORMAL_MENU_FG_COLOR;
        this.bgColor = NORMAL_MENU_BG_COLOR;
        recalculateSizes(terminalSize);
    }

    public void setScreen(Screen screen) {
        this.screen = screen;
    }

    public KeyStroke readKey() {
        try {
            return screen.pollInput();
        } catch (IOException e) {
            log.warn("Error reading key from screen, window might be closed", e);
            setEndOfGame(true);
        }
        return null;
    }

    public boolean isEndOfGame(KeyStroke keyStroke) {
        return endOfGame;
    }

    @Override
    public Point getScrollOffset() {
        return this.scrollOffset;
    }

    @Override
    public void setScrollOffset(Point pos) {
        this.scrollOffset = pos;
        if (TerminalView.this.gameViewListener != null) {
            TerminalView.this.gameViewListener.onMapPageChanged(scrollOffset, getCols(), getRows());
        }
    }

    @Override
    public Point getMapScrollPage() {
        int cols = getCols();
        int rows = getRows();
        int pageX = cols > 0 ? (int) Math.floor((double) scrollOffset.getX() / cols) : 0;
        int pageY = rows > 0 ? (int) Math.floor((double) scrollOffset.getY() / rows) : 0;
        return new Point(pageX, pageY);
    }

    @Override
    public void setMapScrollPage(Point pos) {
        setScrollOffset(new Point(pos.getX() * getCols(), pos.getY() * getRows()));
    }

    @Override
    public void setStatusBarText(String text) {
        // Implementation for status bar updates in terminal
    }

    @Override
    public void setInfoBarText(String text) {
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (i + 3 < menuBoxSize.getRows()) {
                menuBox.putString(menuBoxPosition.withRelative(1, 3 + i), lines[i]);
            }
        }
    }

    @Override
    public void paint() {
        TerminalSize changedSize = screen.doResizeIfNecessary();
        if (changedSize != null) {
            terminalSize = changedSize;
            recalculateSizes(terminalSize);
            TerminalView.this.gameViewListener.onScreenResized(gameBoxSize.getColumns(),
                    gameBoxSize.getRows());
            gameBox.fillRectangle(gameBoxPosition, gameBoxSize, ' ');
        }

        try {
            this.screen.refresh();
            Thread.yield();
        } catch (IOException e) {
            log.error("Error refreshing screen, window might be closed", e);
            setEndOfGame(true);
        }
    }

    @Override
    public int getCols() {
        return gameBoxSize.getColumns();
    }

    @Override
    public int getRows() {
        return gameBoxSize.getRows();
    }

    @Override
    public void setMenu(List<GameModeMenuOption> options) {
        int length = 1;
        for (GameModeMenuOption option : options) {
            String[] parts = option.gameModeName().split("&");
            String firstPart = parts[0];
            String shortcutPart = parts[1].substring(0, 1);
            String thirdPart = parts[1].substring(1);

            menuBox.setForegroundColor(NORMAL_MENU_FG_COLOR);
            if (!option.enabledIf().get()) {
                menuBox.setForegroundColor(DISABLED_FG_COLOR);
            }

            if (option.selectedIf().get()) {
                menuBox.setBackgroundColor(SELECTED_BG_COLOR);
            } else {
                menuBox.setBackgroundColor(NORMAL_MENU_BG_COLOR);
            }
            menuBox.putString(menuBoxPosition.withRelative(length, 1), firstPart);
            length += firstPart.length();

            menuBox.setForegroundColor(SHORTCUT_COLOR);
            menuBox.putString(menuBoxPosition.withRelative(length, 1), shortcutPart);
            length += shortcutPart.length();

            menuBox.setForegroundColor(NORMAL_MENU_FG_COLOR);
            if (!option.enabledIf().get()) {
                menuBox.setForegroundColor(DISABLED_FG_COLOR);
            }
            menuBox.putString(menuBoxPosition.withRelative(length, 1), thirdPart);
            menuBox.setBackgroundColor(NORMAL_MENU_BG_COLOR);
            length += thirdPart.length() + 1;
            if (option.selectedIf().get()) {
                setHelpBarText(option.gameModeDescription());
            }
        }
    }

    @Override
    public void setHelpBarText(String text) {
        menuBox.putString(menuBoxPosition.withRelative(1, 2), text);
    }

    @Override
    public void set(int x, int y, String c) {
        x -= scrollOffset.getX();
        y -= scrollOffset.getY();
        if (x >= 0 && x < getCols() && y >= 0 && y < getRows()) {
            for (int i = 0; i < c.length(); i++) {
                gameBox.setCharacter(
                        x + i,
                        y,
                        TextCharacter.fromCharacter(
                                c.charAt(i),
                                fgColor,
                                bgColor)[0]);
            }
        }
    }

    public TextColor getFgColor() {
        return fgColor;
    }

    public void setFgColor(TextColor color) {
        this.fgColor = color;
    }

    public void setBgColor(TextColor color) {
        this.bgColor = color;
    }

    @Override
    public void setPageOfPos(int x, int y) {
        centerOn(x, y);
    }

    @Override
    public void centerOn(int x, int y) {
        int cols = getCols();
        int rows = getRows();
        setScrollOffset(new Point(x - cols / 2, y - rows / 2));
    }

    @Override
    public void ensureVisible(int x, int y, int margin) {
        int cols = getCols();
        int rows = getRows();
        if (cols <= 0 || rows <= 0) return;
        int minX = scrollOffset.getX() + margin;
        int maxX = scrollOffset.getX() + cols - 1 - margin;
        int minY = scrollOffset.getY() + margin;
        int maxY = scrollOffset.getY() + rows - 1 - margin;
        int newScrollX = scrollOffset.getX();
        int newScrollY = scrollOffset.getY();
        if (x < minX) {
            newScrollX = x - margin;
        } else if (x > maxX) {
            newScrollX = x - (cols - 1 - margin);
        }
        if (y < minY) {
            newScrollY = y - margin;
        } else if (y > maxY) {
            newScrollY = y - (rows - 1 - margin);
        }
        if (newScrollX != scrollOffset.getX() || newScrollY != scrollOffset.getY()) {
            setScrollOffset(new Point(newScrollX, newScrollY));
        }
    }

    @Override
    public void clear(int x, int y) {
        menuBox.setBackgroundColor(NORMAL_MENU_BG_COLOR);
        gameBox.setBackgroundColor(NORMAL_MENU_BG_COLOR);
        menuBox.setForegroundColor(NORMAL_MENU_FG_COLOR);
        gameBox.setForegroundColor(NORMAL_MENU_FG_COLOR);
        set(x, y, " ");
    }

    @Override
    public void fill(int x, int y, int width, int height, String c) {
        for (int col = x; col < x + width; col++) {
            for (int row = y; row < y + height; row++) {
                set(col, row, c);
            }
        }
    }

    @Override
    public void box(int x, int y, int width, int height) {
        fill(x, y, width, 1, "-");
        fill(x, y + height, width, 1, "-");
        fill(x, y, 1, height, "|");
        fill(x + width, y, 1, height, "|");
        set(x, y, "+");
        set(x, y + height, "+");
        set(x + width, y, "+");
        set(x + width, y + height, "+");
    }

    @Override
    public void clear() {
        menuBox.setBackgroundColor(NORMAL_MENU_BG_COLOR);
        gameBox.setBackgroundColor(NORMAL_MENU_BG_COLOR);
        menuBox.setForegroundColor(NORMAL_MENU_FG_COLOR);
        gameBox.setForegroundColor(NORMAL_MENU_FG_COLOR);
        menuBox.fillRectangle(menuBoxPosition, menuBoxSize, ' ');
        gameBox.fillRectangle(gameBoxPosition, gameBoxSize, ' ');
    }

    void recalculateSizes(TerminalSize terminalSize) {
        int cols = Math.max(1, terminalSize.getColumns());
        int rows = Math.max(1, terminalSize.getRows() - 7);
        gameBoxSize = new TerminalSize(cols, rows);
        gameBoxPosition = TerminalPosition.TOP_LEFT_CORNER;
        Page.setWidth(gameBoxSize.getColumns());
        Page.setHeight(gameBoxSize.getRows());
        menuBoxSize = new TerminalSize(terminalSize.getColumns(), Math.min(7, terminalSize.getRows()));
        menuBoxPosition = new TerminalPosition(0, Math.max(0, terminalSize.getRows() - menuBoxSize.getRows()));
    }

    Screen createScreen(Terminal terminal) throws IOException {
        Screen screen;
        screen = new TerminalScreen(terminal);
        screen.startScreen();
        screen.setCursorPosition(null);
        return screen;
    }

    @Override
    public void showSaveDialog() {
        MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);
        File result = new FileDialogBuilder()
                .setTitle("Save File")
                .setDescription("Choose a file:")
                .setActionLabel(LocalizedString.Save.toString())
                .build()
                .showDialog(gui);
        TerminalView.this.gameViewListener.onSaveGame(result);
    }

    @Override
    public void showLoadDialog() {
        MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);

        File result = new FileDialogBuilder()
                .setTitle("Open File")
                .setDescription("Choose a file:")
                .setActionLabel(LocalizedString.Open.toString())
                .build()
                .showDialog(gui);
        TerminalView.this.gameViewListener.onLoadGame(result);
    }

    @Override
    public void showIDE() {
        MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);
        BasicWindow window = new BasicWindow();
        window.setTitle("LT-IDE v1.1 - LeTrain Integrated Development Environment (2D)");
        window.setHints(Arrays.asList(Window.Hint.CENTERED, Window.Hint.EXPANDED));

        Panel mainPanel = new Panel(new BorderLayout());

        // Editor Area
        final TextBox editor = new TextBox(new TerminalSize(60, 20), gameViewListener.getProgram(),
                TextBox.Style.MULTI_LINE);
        mainPanel.addComponent(editor, BorderLayout.Location.CENTER);

        // Side Panel (Reference)
        Panel sidePanel = new Panel(new LinearLayout(Direction.VERTICAL));
        sidePanel.addComponent(new Label("QUICK REFERENCE").setLabelWidth(30));

        ActionListBox refList = new ActionListBox(new TerminalSize(30, 8));
        String[][] refs = {
                { "ITINERARY DSL", "" },
                { "  create itinerary", "create itinerary \"Ruta\" {\n  add station \"A\"\n}" },
                { "  add station with cmd", "add station \"A\" LOAD UNLOAD" },
                { "  add sensor with cmd", "add sensor \"S1\" WAIT 5" },
                { "  path commands", "LOAD | UNLOAD | REVERSE | STOP" },
                { "  path commands", "WAIT n | SPEED n" },
                { "  assign itinerary", "assign itinerary \"Ruta\" to train 1;" },
                { "  set autopilot", "train 1 set autopilot true;" },
                { "", "" },
                { "TRIGGERS", "" },
                { "  sensor entry", "sensor 1 on train enter {\n  \n}" },
                { "  sensor exit", "sensor 1 on train exit {\n  \n}" },
                { "  fork entry", "fork 1 on train enter {\n  \n}" },
                { "  fork exit", "fork 1 on train exit {\n  \n}" },
                { "  semaphore entry", "semaphore 1 on train enter {\n  \n}" },
                { "  semaphore exit", "semaphore 1 on train exit {\n  \n}" },
                { "  station entry", "station 1 on train enter {\n  \n}" },
                { "  station exit", "station 1 on train exit {\n  \n}" },
                { "  train enter/exit", "train 1 on enter {\n  \n}" },
                { "  train link/unlink", "train 1 on link {\n  \n}" },
                { "  train crash", "train 1 on crash {\n  \n}" },
                { "  train contact", "train 1 on contact {\n  \n}" },
                { "", "" },
                { "ACTIONS", "" },
                { "  train speed", "train 1 set speed 5;" },
                { "  train stop", "train 1 stop;" },
                { "  train accelerate", "train 1 accelerate;" },
                { "  train decelerate", "train 1 decelerate;" },
                { "  train invert", "train 1 invert;" },
                { "  train forward", "train 1 set forward;" },
                { "  train backward", "train 1 set backward;" },
                { "  train load", "train 1 load;" },
                { "  train unload", "train 1 unload;" },
                { "  train link", "train 1 link forward 1;" },
                { "  train unlink", "train 1 unlink back 1;" },
                { "  train at station", "train at station 1 stop;" },
                { "  train at sensor", "train at sensor 1 stop;" },
                { "  fork straight", "fork 1 set straight;" },
                { "  fork curved", "fork 1 set curved;" },
                { "  fork dir", "fork 1 set E;" },
                { "  semaphore open", "semaphore 1 set open;" },
                { "  semaphore closed", "semaphore 1 set closed;" },
                { "", "" },
                { "SET NAMES", "" },
                { "  station name", "station 1 set name \"Madrid\";" },
                { "  sensor name", "sensor 1 set name \"S1\";" },
                { "  train name", "train 1 set name \"Express\";" }
        };
        for (String[] r : refs) {
            String label = r[0];
            String snippet = r[1];
            if (label.isEmpty())
                continue;
            if (snippet.isEmpty()) {
                refList.addItem("[" + label + "]", () -> {
                });
            } else {
                refList.addItem(label, () -> insertAtCaret(editor, snippet));
            }
        }
        sidePanel.addComponent(refList);

        sidePanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
        sidePanel.addComponent(new Label("OBJECTS STATUS:"));
        TextBox objectsStatus = new TextBox(new TerminalSize(30, 4), gameViewListener.getGameObjectsReport());
        objectsStatus.setReadOnly(true);
        sidePanel.addComponent(objectsStatus);

        sidePanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
        sidePanel.addComponent(new Label("LATEST LOGS:"));
        List<String> logs = gameViewListener.getEventLogEntries();
        int start = Math.max(0, logs.size() - 5);
        String recentLogs = String.join("\n", logs.subList(start, logs.size()));
        TextBox logsBox = new TextBox(new TerminalSize(30, 4), recentLogs);
        logsBox.setReadOnly(true);
        sidePanel.addComponent(logsBox);

        mainPanel.addComponent(sidePanel, BorderLayout.Location.RIGHT);

        // Footer (Buttons)
        Panel footer = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Runnable applyAction = () -> {
            gameViewListener.onEditCommands(editor.getText());
            window.close();
        };
        Runnable saveAction = () -> {
            gameViewListener.onEditCommands(editor.getText());
            showSaveDialog();
        };
        Runnable loadAction = () -> {
            showLoadDialog();
            window.close();
        };
        Runnable cancelAction = window::close;

        com.googlecode.lanterna.gui2.InteractableRenderer<Button> mnemonicRenderer = new com.googlecode.lanterna.gui2.InteractableRenderer<Button>() {
            @Override
            public com.googlecode.lanterna.TerminalSize getPreferredSize(Button component) {
                return new com.googlecode.lanterna.TerminalSize(component.getLabel().length() + 4, 1);
            }
            @Override
            public void drawComponent(com.googlecode.lanterna.gui2.TextGUIGraphics graphics, Button component) {
                if (component.isFocused()) {
                    graphics.applyThemeStyle(component.getThemeDefinition().getActive());
                } else {
                    graphics.applyThemeStyle(component.getThemeDefinition().getNormal());
                }
                String label = component.getLabel();
                graphics.putString(0, 0, "< " + label + " >");
                graphics.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.YELLOW_BRIGHT);
                if (label.length() > 0) {
                    graphics.putString(2, 0, label.substring(0, 1));
                }
            }
            @Override
            public com.googlecode.lanterna.TerminalPosition getCursorLocation(Button component) {
                return null;
            }
        };

        Button applyBtn = new Button("Apply", applyAction);
        applyBtn.setRenderer(mnemonicRenderer);
        footer.addComponent(applyBtn);

        Button saveBtn = new Button("Save", saveAction);
        saveBtn.setRenderer(mnemonicRenderer);
        footer.addComponent(saveBtn);

        Button loadBtn = new Button("Load", loadAction);
        loadBtn.setRenderer(mnemonicRenderer);
        footer.addComponent(loadBtn);

        Button cancelBtn = new Button("Cancel", cancelAction);
        cancelBtn.setRenderer(mnemonicRenderer);
        footer.addComponent(cancelBtn);
        window.addWindowListener(new com.googlecode.lanterna.gui2.WindowListenerAdapter() {
            @Override
            public void onInput(com.googlecode.lanterna.gui2.Window w, com.googlecode.lanterna.input.KeyStroke ks, java.util.concurrent.atomic.AtomicBoolean deliverEvent) {
                if (ks.isAltDown() && ks.getCharacter() != null) {
                    char c = Character.toLowerCase(ks.getCharacter());
                    if (c == 'a') { applyAction.run(); deliverEvent.set(false); }
                    else if (c == 's') { saveAction.run(); deliverEvent.set(false); }
                    else if (c == 'l') { loadAction.run(); deliverEvent.set(false); }
                    else if (c == 'c') { cancelAction.run(); deliverEvent.set(false); }
                }
            }
        });

        mainPanel.addComponent(footer, BorderLayout.Location.BOTTOM);

        window.setComponent(mainPanel);
        gui.addWindowAndWait(window);
    }

    private void insertAtCaret(TextBox editor, String text) {
        String current = editor.getText();
        TerminalPosition pos = editor.getCaretPosition();

        // Convert TerminalPosition to linear offset
        String[] lines = current.split("\n", -1);
        int offset = 0;
        for (int i = 0; i < pos.getRow() && i < lines.length; i++) {
            offset += lines[i].length() + 1; // +1 for the newline
        }
        offset += pos.getColumn();
        offset = Math.min(offset, current.length());

        String before = current.substring(0, offset);
        String after = current.substring(offset);
        editor.setText(before + text + after);

        // Refocus and place caret after insertion would be nice, but
        // setCaretPosition with TerminalPosition is complex to calculate accurately
        // with multi-line snippets.
        // For now, refocusing will suffice as the user can see the change.
        editor.takeFocus();
    }

    @Override
    public void showExitDialog() {
        MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);
        BasicWindow window = new BasicWindow();
        window.setHints(Arrays.asList(Window.Hint.CENTERED));
        window.setTitle("LeTrain");
        Panel contentPanel = new Panel();
        contentPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));
        contentPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
        contentPanel.addComponent(new Button("Exit", new Runnable() {
            @Override
            public void run() {
                setEndOfGame(true);
                window.close();
            }
        }));
        contentPanel.addComponent(new Button("Play!", new Runnable() {
            @Override
            public void run() {
                window.close();
            }
        }));

        window.setComponent(contentPanel);
        gui.addWindowAndWait(window);
    }

    protected void setEndOfGame(boolean endOfGame) {
        try {
            this.screen.doResizeIfNecessary();
        } catch (Exception e) {
            // Ignored on shutdown
        }
        this.endOfGame = endOfGame;
    }

    public void stop() {
        try {
            if (screen != null) {
                screen.stopScreen();
            }
        } catch (IOException e) {
            log.warn("Error stopping screen", e);
        }
    }

    @Override
    public void showMessage(String title, String message) {
        MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);
        com.googlecode.lanterna.gui2.dialogs.MessageDialog.showMessageDialog(gui, title, message);
    }

    @Override
    public void showReferenceGuide() {
        String guide = "LeTrain Automation Reference:\n\n" +
                "── Itinerary DSL (auto-navigation) ──\n" +
                "  create itinerary \"name\" {\n" +
                "    add station \"A\" [cmd] [cmd] ...\n" +
                "    add sensor \"S1\" [cmd] ...\n" +
                "  }\n" +
                "  assign itinerary \"name\" to train ID;\n" +
                "  train ID set autopilot true|false;\n" +
                "  Waypoint commands: LOAD UNLOAD REVERSE STOP\n" +
                "                     WAIT n   SPEED n\n\n" +
                "── Triggers (event-based) ──\n" +
                "  sensor ID on train enter|exit { actions }\n" +
                "  station ID on train enter|exit { actions }\n" +
                "  fork ID on train enter|exit { actions }\n" +
                "  semaphore ID on train enter|exit { actions }\n" +
                "  train ID on enter|exit|link|unlink|crash|contact { actions }\n\n" +
                "── Actions ──\n" +
                "  train set speed N      train stop\n" +
                "  train accelerate        train decelerate\n" +
                "  train invert            train set forward|backward\n" +
                "  train load              train unload\n" +
                "  train link fwd|back [N] train unlink fwd|back [N]\n" +
                "  fork ID set straight|curved|flip|dir\n" +
                "  semaphore ID set open|closed\n" +
                "  train at station ID     train at sensor ID\n" +
                "  train at fork ID        train at semaphore ID\n\n" +
                "── Set names ──\n" +
                "  station ID set name \"...\"\n" +
                "  sensor  ID set name \"...\"\n" +
                "  train   ID set name \"...\"\n\n" +
                "Examples:\n" +
                "  create itinerary \"Ruta\" {\n" +
                "    add station \"Madrid\" LOAD\n" +
                "    add station \"Barcelona\" UNLOAD\n" +
                "  }\n" +
                "  assign itinerary \"Ruta\" to train 1;\n" +
                "  train 1 set autopilot true;\n" +
                "  train 1 set speed 3;\n" +
                "  ---\n" +
                "  station 1 on train enter { train load; train unlink back 1; }\n" +
                "  train 1 on crash { train set speed 0; }";
        showMessage("Automation Cheat Sheet", guide);
    }
}
