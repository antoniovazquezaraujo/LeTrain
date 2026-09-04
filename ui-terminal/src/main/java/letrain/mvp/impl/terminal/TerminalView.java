package letrain.mvp.impl.terminal;

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
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import letrain.map.Page;
import letrain.map.Point;
import letrain.mvp.GameViewListener;
import letrain.mvp.Model.GameModeMenuOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modernized Terminal implementation of the View interface using Lanterna. This class isolates
 * terminal-specific logic and UI handling.
 */
public class TerminalView implements letrain.mvp.View {
    private String overlayTitle;
    private String overlayMessage;
    private int overlayScroll = 0;
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
    private boolean isUnderline = false;
    private boolean isBlink = false;
    private int cameraDeadzone = 1;
    private boolean cameraPagination = false;
    private int flashDeadzoneTicks = 0;
    boolean endOfGame = false;
    private int helpLevel = 0;

    @Override
    public void setHelpLevel(int helpLevel) {
        if (this.helpLevel == helpLevel) {
            return;
        }
        this.helpLevel = helpLevel;
        if (terminalSize != null) {
            recalculateSizes(terminalSize);
            if (this.gameViewListener != null) {
                this.gameViewListener.onScreenResized(gameBoxSize.getColumns(),
                        gameBoxSize.getRows());
            }
        }
    }

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
                        com.googlecode.lanterna.terminal.swing.SwingTerminalFontConfiguration
                                .newInstance(customFont));
            } else {
                log.warn("Font resource /fonts/JuliaMono-Regular.ttf not found in classpath!");
            }

        } catch (Exception e) {
            log.warn("Error loading custom JuliaMono font for terminal: {}", e.getMessage(), e);
        }

        try {
            terminalFactory.setUnixTerminalCtrlCBehaviour(
                    com.googlecode.lanterna.terminal.ansi.UnixLikeTerminal.CtrlCBehaviour.TRAP);
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

    private void putStringWithColors(com.googlecode.lanterna.graphics.TextGraphics tg, com.googlecode.lanterna.TerminalPosition pos, String text) {
        int x = pos.getColumn();
        int y = pos.getRow();
        com.googlecode.lanterna.TextColor defaultColor = tg.getForegroundColor();
        
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("<<([A-Z_]+)>>");
        java.util.regex.Matcher m = p.matcher(text);
        
        int lastEnd = 0;
        while (m.find()) {
            String plain = text.substring(lastEnd, m.start());
            if (!plain.isEmpty()) {
                tg.putString(x, y, plain);
                x += plain.length();
            }
            String color = m.group(1);
            if (color.equals("GREEN")) tg.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.GREEN_BRIGHT);
            else if (color.equals("RED")) tg.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.RED_BRIGHT);
            else if (color.equals("YELLOW")) tg.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.YELLOW);
            else if (color.equals("WHITE")) tg.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.WHITE_BRIGHT);
            else if (color.equals("RESET")) tg.setForegroundColor(defaultColor);
            
            lastEnd = m.end();
        }
        String plain = text.substring(lastEnd);
        if (!plain.isEmpty()) {
            tg.putString(x, y, plain);
        }
        tg.setForegroundColor(defaultColor);
    }

    @Override
    public void setInfoBarText(String text) {
        if (helpLevel == 0) {
            return;
        }
        String[] lines = text.split("\n");
        int offset = 2; // Always start at row 2
        for (int i = 0; i < lines.length; i++) {
            if (i + offset < menuBoxSize.getRows()) {
                menuBox.setForegroundColor(DISABLED_FG_COLOR);
                putStringWithColors(menuBox, menuBoxPosition.withRelative(1, offset + i), lines[i]);
            }
        }
        menuBox.setForegroundColor(NORMAL_MENU_FG_COLOR);
    }

    @Override
    public boolean isCameraPagination() {
        return this.cameraPagination;
    }

    @Override
    public void setCameraPagination(boolean paginate) {
        this.cameraPagination = paginate;
    }

    @Override
    public int getCameraDeadzone() {
        return this.cameraDeadzone;
    }

    @Override
    public void setCameraDeadzone(int margin) {
        this.cameraDeadzone = margin;
    }

    @Override
    public void flashCameraDeadzone() {
        this.flashDeadzoneTicks = 6; // Paint for 2 frames
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
            if (flashDeadzoneTicks > 0) {
                flashDeadzoneTicks--;
                int radius = getCameraDeadzone();
                if (radius >= 0) {
                    int cols = getCols();
                    int rows = getRows();
                    int centerX = cols / 2;
                    int centerY = rows / 2;

                    int radiusX = (int) Math.round(radius * ((double) cols / rows));
                    int radiusY = radius;

                    int screenMinX = centerX - radiusX;
                    int screenMaxX = centerX + radiusX;
                    int screenMinY = centerY - radiusY;
                    int screenMaxY = centerY + radiusY;

                    if (radius >= 999) {
                        screenMinX = 0;
                        screenMaxX = cols - 1;
                        screenMinY = 0;
                        screenMaxY = rows - 1;
                    }

                    screenMinX = Math.max(0, screenMinX);
                    screenMaxX = Math.min(cols - 1, screenMaxX);
                    screenMinY = Math.max(0, screenMinY);
                    screenMaxY = Math.min(rows - 1, screenMaxY);

                    if (screenMinX <= screenMaxX && screenMinY <= screenMaxY) {
                        com.googlecode.lanterna.TextColor dotColor =
                                cameraPagination ? com.googlecode.lanterna.TextColor.ANSI.RED_BRIGHT
                                        : com.googlecode.lanterna.TextColor.ANSI.BLUE_BRIGHT;
                        com.googlecode.lanterna.TextCharacter dot =
                                new com.googlecode.lanterna.TextCharacter('·', dotColor,
                                        com.googlecode.lanterna.TextColor.ANSI.BLACK);
                        for (int x = screenMinX; x <= screenMaxX; x++) {
                            gameBox.setCharacter(x, screenMinY, dot);
                            gameBox.setCharacter(x, screenMaxY, dot);
                        }
                        for (int y = screenMinY; y <= screenMaxY; y++) {
                            gameBox.setCharacter(screenMinX, y, dot);
                            gameBox.setCharacter(screenMaxX, y, dot);
                        }
                    }
                }
            }
            
            if (overlayMessage != null) {
                int width = Math.min(60, screen.getTerminalSize().getColumns() - 2);
                int height = Math.min(25, screen.getTerminalSize().getRows() - 2);
                int startX = screen.getTerminalSize().getColumns() - width - 1;
                int startY = 1;
                
                com.googlecode.lanterna.graphics.TextGraphics tg = screen.newTextGraphics();
                tg.setBackgroundColor(com.googlecode.lanterna.TextColor.ANSI.BLUE);
                tg.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.WHITE);
                tg.fillRectangle(new com.googlecode.lanterna.TerminalPosition(startX, startY), new com.googlecode.lanterna.TerminalSize(width, height), ' ');
                
                // Draw title
                if (overlayTitle != null) {
                    tg.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.GREEN_BRIGHT);
                    tg.putString(startX + 2, startY + 1, "== " + overlayTitle + " ==");
                    tg.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.WHITE);
                }
                
                // Draw message
                String[] lines = overlayMessage.split("\n");
                
                // Enforce max scroll
                int maxScroll = Math.max(0, lines.length - (height - 4));
                if (overlayScroll > maxScroll) overlayScroll = maxScroll;
                
                for (int i = 0; i < lines.length - overlayScroll && i < height - 4; i++) {
                    String line = lines[i + overlayScroll];
                    if (line.length() > width - 4) {
                        line = line.substring(0, width - 4) + "...";
                    }
                    tg.putString(startX + 2, startY + 3 + i, line);
                }
                
                if (overlayScroll > 0) {
                    tg.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.MAGENTA);
                    tg.putString(startX + width - 3, startY + 3, "^");
                }
                if (lines.length - overlayScroll > height - 4) {
                    tg.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.MAGENTA);
                    tg.putString(startX + width - 3, startY + height - 2, "v");
                }
                
                
                tg.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.YELLOW);
                tg.putString(startX + 2, startY + height - 1, "[ESC to close | Up/Down to scroll]");
            }

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
        if (helpLevel == 0) {
            return;
        }
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

            if (!option.enabledIf().get()) {
                menuBox.setForegroundColor(DISABLED_FG_COLOR);
            } else {
                menuBox.setForegroundColor(SHORTCUT_COLOR);
            }
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
        if (helpLevel < 2) {
            return;
        }
        menuBox.setForegroundColor(DISABLED_FG_COLOR);
        menuBox.putString(menuBoxPosition.withRelative(1, 4), text);
        menuBox.setForegroundColor(NORMAL_MENU_FG_COLOR);
    }

    @Override
    public void set(int x, int y, String c) {
        x -= scrollOffset.getX();
        y -= scrollOffset.getY();
        if (x >= 0 && x < getCols() && y >= 0 && y < getRows()) {
            java.util.List<com.googlecode.lanterna.SGR> sgrList = new java.util.ArrayList<>();
            if (isUnderline) {
                sgrList.add(com.googlecode.lanterna.SGR.UNDERLINE);
            }
            if (isBlink) {
                sgrList.add(com.googlecode.lanterna.SGR.BLINK);
            }
            com.googlecode.lanterna.SGR[] modifiers =
                    sgrList.toArray(new com.googlecode.lanterna.SGR[0]);
            for (int i = 0; i < c.length(); i++) {
                gameBox.setCharacter(x + i, y,
                        TextCharacter.fromCharacter(c.charAt(i), fgColor, bgColor, modifiers)[0]);
            }
        }
    }

    public void setUnderline(boolean enable) {
        this.isUnderline = enable;
    }

    public void setBlink(boolean enable) {
        this.isBlink = enable;
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
    public void ensureVisible(int x, int y, int radius, boolean paginate) {
        int cols = getCols();
        int rows = getRows();
        if (cols <= 0 || rows <= 0) {
            return;
        }

        int centerX = scrollOffset.getX() + cols / 2;
        int centerY = scrollOffset.getY() + rows / 2;

        int radiusX = (int) Math.round(radius * ((double) cols / rows));
        int radiusY = radius;

        int minX = centerX - radiusX;
        int maxX = centerX + radiusX;
        int minY = centerY - radiusY;
        int maxY = centerY + radiusY;

        if (radius >= 999) {
            minX = scrollOffset.getX();
            maxX = scrollOffset.getX() + cols - 1;
            minY = scrollOffset.getY();
            maxY = scrollOffset.getY() + rows - 1;
        }

        int newScrollX = scrollOffset.getX();
        int newScrollY = scrollOffset.getY();

        if (x < minX) {
            newScrollX -= (paginate ? Math.max(1, maxX - minX) : (minX - x));
        } else if (x > maxX) {
            newScrollX += (paginate ? Math.max(1, maxX - minX) : (x - maxX));
        }

        if (y < minY) {
            newScrollY -= (paginate ? Math.max(1, maxY - minY) : (minY - y));
        } else if (y > maxY) {
            newScrollY += (paginate ? Math.max(1, maxY - minY) : (y - maxY));
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
        int reservedRows = (helpLevel == 2) ? 7 : (helpLevel == 1 ? 4 : 0);
        int rows = Math.max(1, terminalSize.getRows() - reservedRows);
        gameBoxSize = new TerminalSize(cols, rows);
        gameBoxPosition = TerminalPosition.TOP_LEFT_CORNER;
        Page.setWidth(gameBoxSize.getColumns());
        Page.setHeight(gameBoxSize.getRows());
        menuBoxSize = new TerminalSize(terminalSize.getColumns(),
                Math.min(reservedRows, terminalSize.getRows()));
        menuBoxPosition = new TerminalPosition(0,
                Math.max(0, terminalSize.getRows() - menuBoxSize.getRows()));
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
        File result = new FileDialogBuilder().setTitle("Save File").setDescription("Choose a file:")
                .setActionLabel(LocalizedString.Save.toString()).build().showDialog(gui);
        TerminalView.this.gameViewListener.onSaveGame(result);
    }

    @Override
    public void showLoadDialog() {
        MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);

        File result = new FileDialogBuilder().setTitle("Open File").setDescription("Choose a file:")
                .setActionLabel(LocalizedString.Open.toString()).build().showDialog(gui);
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

        ActionListBox refList = new ActionListBox(new TerminalSize(30, 20)) {
            private long lastClickTime = 0;

            @Override
            public com.googlecode.lanterna.gui2.Interactable.Result handleKeyStroke(
                    com.googlecode.lanterna.input.KeyStroke ks) {
                if (ks instanceof com.googlecode.lanterna.input.MouseAction) {
                    com.googlecode.lanterna.input.MouseAction mi =
                            (com.googlecode.lanterna.input.MouseAction) ks;
                    if (mi.getActionType() == com.googlecode.lanterna.input.MouseActionType.CLICK_RELEASE) {
                        super.handleKeyStroke(ks);
                        long now = System.currentTimeMillis();
                        if (now - lastClickTime < 500) {
                            return super.handleKeyStroke(
                                    new com.googlecode.lanterna.input.KeyStroke(
                                            com.googlecode.lanterna.input.KeyType.Enter));
                        }
                        lastClickTime = now;
                        return com.googlecode.lanterna.gui2.Interactable.Result.HANDLED;
                    }
                }
                return super.handleKeyStroke(ks);
            }
        };
        Runnable updateList = new Runnable() {
            private void build(letrain.command.GrammarReference.Node node, String indent) {
                if (node.isHeading) {
                    refList.addItem(node.label, () -> {
                    });
                } else if (node.snippet != null && node.children.isEmpty()) {
                    refList.addItem(indent + node.label, () -> {
                        insertAtCaret(editor, node.snippet);
                    });
                } else {
                    String prefix = node.expanded ? "[-]" : "[+]";
                    refList.addItem(indent + prefix + " " + node.label, () -> {
                        node.setExpanded(!node.expanded);
                        this.run();
                        refList.takeFocus();
                    });
                    if (node.expanded) {
                        for (letrain.command.GrammarReference.Node child : node.children) {
                            build(child, indent + "  ");
                        }
                    }
                }
            }

            @Override
            public void run() {
                int selected = refList.getSelectedIndex();
                refList.clearItems();
                for (letrain.command.GrammarReference.Node rootNode : letrain.command.GrammarReference
                        .getReferenceTree()) {
                    build(rootNode, "");
                }
                if (selected >= 0 && selected < refList.getItems().size()) {
                    refList.setSelectedIndex(selected);
                }
            }
        };
        updateList.run();
        sidePanel.addComponent(refList);

        sidePanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
        sidePanel.addComponent(new Label("OBJECTS STATUS:"));
        TextBox objectsStatus =
                new TextBox(new TerminalSize(30, 4), gameViewListener.getGameObjectsReport());
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

        com.googlecode.lanterna.gui2.InteractableRenderer<Button> mnemonicRenderer =
                new com.googlecode.lanterna.gui2.InteractableRenderer<Button>() {
                    @Override
                    public com.googlecode.lanterna.TerminalSize getPreferredSize(Button component) {
                        return new com.googlecode.lanterna.TerminalSize(
                                component.getLabel().length() + 4, 1);
                    }

                    @Override
                    public void drawComponent(com.googlecode.lanterna.gui2.TextGUIGraphics graphics,
                            Button component) {
                        if (component.isFocused()) {
                            graphics.applyThemeStyle(component.getThemeDefinition().getActive());
                        } else {
                            graphics.applyThemeStyle(component.getThemeDefinition().getNormal());
                        }
                        String label = component.getLabel();
                        graphics.putString(0, 0, "< " + label + " >");
                        graphics.setForegroundColor(
                                com.googlecode.lanterna.TextColor.ANSI.RED_BRIGHT);
                        if (label.length() > 0) {
                            graphics.putString(2, 0, label.substring(0, 1));
                        }
                    }

                    @Override
                    public com.googlecode.lanterna.TerminalPosition getCursorLocation(
                            Button component) {
                        return null;
                    }
                };

        Runnable togglePanelsAction = () -> {
            if (sidePanel.getParent() != null) {
                mainPanel.removeComponent(sidePanel);
                editor.setPreferredSize(new TerminalSize(90, 20));
            } else {
                mainPanel.addComponent(sidePanel, BorderLayout.Location.RIGHT);
                editor.setPreferredSize(new TerminalSize(60, 20));
            }
        };
        Button togglePanelsBtn = new Button("Toggle", togglePanelsAction);
        togglePanelsBtn.setRenderer(mnemonicRenderer);
        footer.addComponent(togglePanelsBtn);

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
            public void onInput(com.googlecode.lanterna.gui2.Window w,
                    com.googlecode.lanterna.input.KeyStroke ks,
                    java.util.concurrent.atomic.AtomicBoolean deliverEvent) {
                if (ks.isAltDown() && ks.getCharacter() != null) {
                    char c = Character.toLowerCase(ks.getCharacter());
                    if (c == 't') {
                        togglePanelsAction.run();
                        deliverEvent.set(false);
                    } else if (c == 'r') {
                        refList.takeFocus();
                        deliverEvent.set(false);
                    } else if (c == 'e') {
                        editor.takeFocus();
                        deliverEvent.set(false);
                    } else if (c == 'a') {
                        applyAction.run();
                        deliverEvent.set(false);
                    } else if (c == 's') {
                        saveAction.run();
                        deliverEvent.set(false);
                    } else if (c == 'l') {
                        loadAction.run();
                        deliverEvent.set(false);
                    } else if (c == 'c') {
                        cancelAction.run();
                        deliverEvent.set(false);
                    }
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

    public boolean isShowingOverlay() {
        return overlayMessage != null;
    }

    public void scrollOverlay(int amount) {
        overlayScroll += amount;
        if (overlayScroll < 0) overlayScroll = 0;
        paint();
    }

    public boolean clearOverlay() {
        if (overlayMessage != null) {
            overlayMessage = null;
            overlayTitle = null;
            overlayScroll = 0;
            // Clear the screen right away to erase the overlay
            try { screen.clear(); } catch (Exception e) {}
            return true;
        }
        return false;
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
        if (screen != null) {
            try {
                screen.stopScreen();
            } catch (Exception e) {
                // Ignore, screen might already be stopped
            }
            try {
                screen.close();
            } catch (Exception e) {
                // Ignore
            }
        }
        if (terminal != null) {
            try {
                terminal.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    @Override
    public void showMessage(String title, String message) {
        this.overlayTitle = title;
        this.overlayMessage = message;
        paint();
    }
    
    @Override
    public void drawCommandLine(String text, String error) {
        int screenRows = screen.getTerminalSize().getRows();
        int screenCols = screen.getTerminalSize().getColumns();
        if (screenRows < 2) return; // safety
        
        int drawY = screenRows - 1; // Last line of the absolute screen
        
        TextGraphics g = screen.newTextGraphics();
        g.setBackgroundColor(TextColor.ANSI.BLACK);
        g.setForegroundColor(TextColor.ANSI.WHITE);
        g.putString(0, drawY, " ".repeat(screenCols)); // clear line
        
        String prompt = ":" + text + "_";
        g.putString(0, drawY, prompt);
        
        if (error != null && !error.isEmpty()) {
            String errStr = " " + error.replace('\n', ' ').replace('\r', ' ') + " "; // Just the error, not [ERROR:]
            int startX = prompt.length() + 2; // small gap
            if (startX < screenCols) {
                // Truncate if it overflows
                if (startX + errStr.length() > screenCols) {
                    errStr = errStr.substring(0, screenCols - startX);
                }
                g.setBackgroundColor(TextColor.ANSI.RED);
                g.setForegroundColor(TextColor.ANSI.WHITE);
                g.putString(startX, drawY, errStr);
            }
        }
    }

}
