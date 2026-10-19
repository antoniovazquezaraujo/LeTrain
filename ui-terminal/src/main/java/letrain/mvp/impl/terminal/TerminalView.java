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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import letrain.map.Page;
import letrain.map.Point;
import letrain.mvp.CoverArt;
import letrain.mvp.EditorText;
import letrain.mvp.FileMenuText;
import letrain.mvp.GameViewListener;
import letrain.mvp.MenuText;
import letrain.mvp.Model.GameModeMenuOption;
import letrain.visitor.terminal.TerminalPalette;
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
    private int overlayWidth = 60;
    private boolean overlayMaximized = false;
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
    /**
     * Full-screen camera dead zone: {@code ensureVisible} treats it as "the whole visible map" and
     * the flash outline as "the screen border" (#697).
     */
    static final int FULL_SCREEN_DEADZONE = 999;

    /**
     * The camera starts with the full-screen dead zone, so it only scrolls near the border (#697).
     */
    private int cameraDeadzone = FULL_SCREEN_DEADZONE;
    private boolean cameraPagination = false;
    private int flashDeadzoneTicks = 0;
    boolean endOfGame = false;
    private int helpLevel = 0;

    /** True while the title cover is on screen; {@link #paint()} draws it instead of the game. */
    private boolean coverVisible = false;

    /** Terminal colour mode used to degrade the coloured cover art; detected once, lazily. */
    private TerminalPalette coverPalette;

    /**
     * Hand-edited scenario text kept between IDE openings; null means "regenerate from the world".
     */
    private String scenarioDraft = null;

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
    /**
     * Disabled menu entries stay readable grey ({@code #808080}, an exact RGB independent of the
     * terminal theme; Lanterna degrades it on 256/16-colour terminals) and only lose the green
     * shortcut (owner feedback). The info/help lines keep {@link #DISABLED_FG_COLOR}.
     */
    static final TextColor MENU_DISABLED_FG_COLOR = new TextColor.RGB(0x80, 0x80, 0x80);
    static final TextColor SELECTED_BG_COLOR = ANSI.BLUE;
    static final TextColor SHORTCUT_COLOR = ANSI.GREEN_BRIGHT;
    /** Accent of the Alt mnemonic letters (footer buttons, dialog tabs): the editor-footer red. */
    static final TextColor MNEMONIC_COLOR = ANSI.RED_BRIGHT;

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
            throw new RuntimeException(
                    "Failed to initialize Lanterna terminal. If you are running this via double-click, try running it from a command line.",
                    e);
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

    /**
     * Shows or hides the title cover; while visible, {@link #paint()} draws it instead of the game.
     */
    public void setCoverVisible(boolean visible) {
        this.coverVisible = visible;
    }

    /** True while the title cover is on screen. */
    public boolean isCoverVisible() {
        return coverVisible;
    }

    /**
     * Style the 2D cover is painted with (issue #771): full colour on real-colour terminals, dots
     * over the terminal background on dumb 16-colour terminals. Package-visible and static so the
     * selection can be pinned without a real terminal; it delegates to
     * {@link CoverStyle#forPalette(TerminalPalette)} so both stay one decision.
     */
    static CoverStyle coverStyle(TerminalPalette palette) {
        return CoverStyle.forPalette(palette);
    }

    /**
     * Paints the cover full-screen and refreshes; called by {@link #paint()} while visible. The
     * coloured braille art carrying the slant {@code LeTrain} logo is painted always, centred and
     * cropped when the terminal is smaller than the artwork; dumb terminals get the dots-only style
     * ({@link #coverStyle(TerminalPalette)}). There is no "press any key" text in 2D (owner
     * decision): any key still continues.
     */
    private void paintCover() {
        if (screen == null) {
            return;
        }
        try {
            TerminalSize size = screen.getTerminalSize();
            TextGraphics tg = screen.newTextGraphics();
            tg.setBackgroundColor(NORMAL_MENU_BG_COLOR);
            tg.setForegroundColor(NORMAL_MENU_FG_COLOR);
            tg.fillRectangle(TerminalPosition.TOP_LEFT_CORNER, size, ' ');
            if (coverPalette == null) {
                coverPalette = TerminalPalette.detect();
            }
            CoverArtPainter.paint(tg, CoverArt.load(), coverPalette, size,
                    coverStyle(coverPalette));
            this.screen.refresh();
            Thread.yield();
        } catch (IOException e) {
            log.error("Error refreshing the cover, window might be closed", e);
            setEndOfGame(true);
        }
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

    private void putStringWithColors(com.googlecode.lanterna.graphics.TextGraphics tg,
            com.googlecode.lanterna.TerminalPosition pos, String text) {
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
            if (color.equals("GREEN"))
                tg.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.GREEN_BRIGHT);
            else if (color.equals("RED"))
                tg.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.RED_BRIGHT);
            else if (color.equals("YELLOW"))
                tg.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.YELLOW);
            else if (color.equals("WHITE"))
                tg.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.WHITE_BRIGHT);
            else if (color.equals("RESET"))
                tg.setForegroundColor(defaultColor);

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

        if (coverVisible) {
            paintCover();
            return;
        }

        try {
            if (flashDeadzoneTicks > 0) {
                flashDeadzoneTicks--;
                int radius = getCameraDeadzone();
                if (radius >= 0) {
                    int[] rect = deadzoneRect(getCols(), getRows(), radius);
                    int screenMinX = rect[0];
                    int screenMinY = rect[1];
                    int screenMaxX = rect[2];
                    int screenMaxY = rect[3];

                    if (screenMinX <= screenMaxX && screenMinY <= screenMaxY) {
                        com.googlecode.lanterna.TextColor dotColor =
                                cameraPagination ? com.googlecode.lanterna.TextColor.ANSI.RED_BRIGHT
                                        : com.googlecode.lanterna.TextColor.ANSI.BLUE_BRIGHT;
                        com.googlecode.lanterna.TextCharacter dot =
                                com.googlecode.lanterna.TextCharacter.fromCharacter('·', dotColor,
                                        com.googlecode.lanterna.TextColor.ANSI.BLACK)[0];
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

            // REC indicator: blinking red "REC" in the top-left corner while the journal records.
            if (gameViewListener.isRecordingCommands()
                    && (System.currentTimeMillis() / 500) % 2 == 0) {
                String rec = "REC";
                for (int i = 0; i < rec.length(); i++) {
                    gameBox.setCharacter(i, 0,
                            TextCharacter.fromCharacter(rec.charAt(i),
                                    com.googlecode.lanterna.TextColor.ANSI.RED_BRIGHT,
                                    com.googlecode.lanterna.TextColor.ANSI.BLACK)[0]);
                }
            }

            // Game clock (ADR-022): dim label in the top-right corner of the map.
            String clock = gameViewListener.getGameTimeText();
            if (clock != null && !clock.isEmpty()) {
                int clockX = gameBoxSize.getColumns() - clock.length() - 1;
                if (clockX > 3) { // keep clear of the REC indicator on the left
                    for (int i = 0; i < clock.length(); i++) {
                        gameBox.setCharacter(clockX + i, 0,
                                TextCharacter.fromCharacter(clock.charAt(i), TextColor.ANSI.YELLOW,
                                        TextColor.ANSI.BLACK)[0]);
                    }
                }
            }

            if (overlayMessage != null) {
                int cols = screen.getTerminalSize().getColumns();
                int rows = screen.getTerminalSize().getRows();
                int width = overlayMaximized ? cols - 2
                        : Math.min(Math.max(20, overlayWidth), cols - 2);
                int height = overlayMaximized ? rows - 2 : Math.min(25, rows - 2);
                int innerWidth = Math.max(10, width - 4);
                int contentRows = Math.max(1, height - 4);
                int startX = overlayMaximized ? 1 : cols - width - 1;
                int startY = 1;

                java.util.List<String> wrapped =
                        wrapLines(overlayMessage.split("\n", -1), innerWidth);
                int maxScroll = Math.max(0, wrapped.size() - contentRows);
                if (overlayScroll > maxScroll)
                    overlayScroll = maxScroll;
                if (overlayScroll < 0)
                    overlayScroll = 0;

                com.googlecode.lanterna.graphics.TextGraphics tg = screen.newTextGraphics();
                tg.setBackgroundColor(com.googlecode.lanterna.TextColor.ANSI.BLUE);
                tg.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.WHITE);
                tg.fillRectangle(new com.googlecode.lanterna.TerminalPosition(startX, startY),
                        new com.googlecode.lanterna.TerminalSize(width, height), ' ');

                // Draw title
                if (overlayTitle != null) {
                    tg.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.GREEN_BRIGHT);
                    tg.putString(startX + 2, startY + 1, "== " + overlayTitle + " ==");
                    tg.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.WHITE);
                }

                for (int i = 0; i < contentRows && i + overlayScroll < wrapped.size(); i++) {
                    tg.putString(startX + 2, startY + 3 + i, wrapped.get(i + overlayScroll));
                }

                if (overlayScroll > 0) {
                    tg.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.MAGENTA);
                    tg.putString(startX + width - 3, startY + 3, "^");
                }
                if (overlayScroll + contentRows < wrapped.size()) {
                    tg.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.MAGENTA);
                    tg.putString(startX + width - 3, startY + height - 2, "v");
                }

                tg.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.YELLOW);
                tg.putString(startX + 2, startY + height - 1,
                        "[ESC close | j/k scroll | h/l width | PgUp/PgDn | F max]");
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
            length = drawMenuOption(menuBox, menuBoxPosition.withRelative(0, 1), option, length);
            if (option.selectedIf().get()) {
                setHelpBarText(MenuText.selectedHint(option.gameModeDescription(),
                        gameViewListener.isRecordingCommands()));
            }
        }
    }

    /**
     * Paints one menu option at {@code offset} columns from {@code origin}: label and suffix in
     * white, the shortcut in green and the candidate mode's background in blue when selected. A
     * disabled option is painted in grey ({@link #MENU_DISABLED_FG_COLOR}) and loses the green
     * shortcut. The label is split with the shared {@link MenuText} parser, the same one the 3D HUD
     * uses, so wording and shortcuts cannot drift between clients. Package-visible and static so a
     * test can check the layout without a real terminal.
     *
     * @return the offset for the next option (plain text plus the separating space)
     */
    static int drawMenuOption(TextGraphics menuBox, TerminalPosition origin,
            GameModeMenuOption option, int offset) {
        MenuText.Label label = MenuText.parse(option.gameModeName());
        boolean enabled = option.enabledIf().get();
        TextColor labelColor = enabled ? NORMAL_MENU_FG_COLOR : MENU_DISABLED_FG_COLOR;
        TextColor hotkeyColor = enabled ? SHORTCUT_COLOR : MENU_DISABLED_FG_COLOR;

        menuBox.setBackgroundColor(
                option.selectedIf().get() ? SELECTED_BG_COLOR : NORMAL_MENU_BG_COLOR);
        if (!label.prefix().isEmpty()) {
            menuBox.setForegroundColor(labelColor);
            menuBox.putString(origin.withRelative(offset, 0), label.prefix());
            offset += label.prefix().length();
        }
        if (label.hasHotkey()) {
            menuBox.setForegroundColor(hotkeyColor);
            menuBox.putString(origin.withRelative(offset, 0), label.hotkey());
            offset += label.hotkey().length();
        }
        if (!label.suffix().isEmpty()) {
            menuBox.setForegroundColor(labelColor);
            menuBox.putString(origin.withRelative(offset, 0), label.suffix());
            offset += label.suffix().length();
        }
        menuBox.setBackgroundColor(NORMAL_MENU_BG_COLOR);
        return offset + 1;
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
        Point next = scrollToKeepVisible(scrollOffset, cols, rows, x, y, radius, paginate);
        if (!next.equals(scrollOffset)) {
            setScrollOffset(next);
        }
    }

    /**
     * Scroll offset that keeps {@code (x, y)} inside the camera dead zone centered on the current
     * viewport. With {@link #FULL_SCREEN_DEADZONE} the dead zone is the whole visible map (#697),
     * so the camera only scrolls when the target is about to leave the screen. Pure and
     * package-visible, so the scroll math can be unit-tested without a terminal.
     */
    static Point scrollToKeepVisible(Point scrollOffset, int cols, int rows, int x, int y,
            int radius, boolean paginate) {
        int centerX = scrollOffset.getX() + cols / 2;
        int centerY = scrollOffset.getY() + rows / 2;

        int radiusX = (int) Math.round(radius * ((double) cols / rows));
        int radiusY = radius;

        int minX = centerX - radiusX;
        int maxX = centerX + radiusX;
        int minY = centerY - radiusY;
        int maxY = centerY + radiusY;

        if (radius >= FULL_SCREEN_DEADZONE) {
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

        return new Point(newScrollX, newScrollY);
    }

    /**
     * Flash rectangle of the dead zone as {@code {minX, minY, maxX, maxY}} in map cells, clamped to
     * the screen. {@link #FULL_SCREEN_DEADZONE} spans the whole visible area, so the preview draws
     * the map border. Pure and package-visible for tests.
     */
    static int[] deadzoneRect(int cols, int rows, int radius) {
        int centerX = cols / 2;
        int centerY = rows / 2;

        int radiusX = (int) Math.round(radius * ((double) cols / rows));
        int radiusY = radius;

        int minX = centerX - radiusX;
        int maxX = centerX + radiusX;
        int minY = centerY - radiusY;
        int maxY = centerY + radiusY;

        if (radius >= FULL_SCREEN_DEADZONE) {
            minX = 0;
            maxX = cols - 1;
            minY = 0;
            maxY = rows - 1;
        }

        minX = Math.max(0, minX);
        maxX = Math.min(cols - 1, maxX);
        minY = Math.max(0, minY);
        maxY = Math.min(rows - 1, maxY);
        return new int[] {minX, minY, maxX, maxY};
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

    /**
     * New Game dialog (S2): seed, terrain sliders with the stacked bar and adaptive preview, and
     * the catalogue sections. Returns the validated settings or {@code null} when cancelled.
     */
    @Override
    public Map<String, String> showNewGameDialog(Map<String, String> currentSettings) {
        // Refresh the front buffer before the dialog freezes it as its backdrop: the File menu or
        // the discard confirmation paint over the last game frame (#742 polish).
        paint();
        return NewGameDialog.show(screen, currentSettings);
    }

    /**
     * In-game Settings dialog (S3b): same form conventions as the New Game dialog plus the apply
     * states and the Apply/Restore/new-game/save-defaults actions. The presenter supplies the
     * current effective values, the scenario-locked keys and the port the actions call.
     */
    public SettingsDialog.Action showSettingsDialog(Map<String, String> currentSettings,
            Set<String> scenarioKeys, SettingsDialog.SettingsPort port) {
        // The File menu just painted over the game: repaint before the dialog captures its backdrop
        // (#742 polish).
        paint();
        return SettingsDialog.show(screen, currentSettings, scenarioKeys, port);
    }

    /** Simple yes/no confirmation (used before discarding a game in progress, #417 pending). */
    @Override
    public boolean confirm(String title, String message) {
        MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);
        com.googlecode.lanterna.gui2.dialogs.MessageDialogButton choice =
                new com.googlecode.lanterna.gui2.dialogs.MessageDialogBuilder().setTitle(title)
                        .setText(message)
                        .addButton(com.googlecode.lanterna.gui2.dialogs.MessageDialogButton.Yes)
                        .addButton(com.googlecode.lanterna.gui2.dialogs.MessageDialogButton.No)
                        .build().showDialog(gui);
        return choice == com.googlecode.lanterna.gui2.dialogs.MessageDialogButton.Yes;
    }

    @Override
    public void showExportDialog() {
        MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);
        File result =
                new FileDialogBuilder().setTitle("Export Scenario").setDescription("Choose a file:")
                        .setActionLabel(LocalizedString.Save.toString()).build().showDialog(gui);
        if (result != null) {
            TerminalView.this.gameViewListener.onExportScenario(result);
        }
    }

    @Override
    public void showImportDialog() {
        MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);
        File result =
                new FileDialogBuilder().setTitle("Import Scenario").setDescription("Choose a file:")
                        .setActionLabel(LocalizedString.Open.toString()).build().showDialog(gui);
        if (result != null) {
            TerminalView.this.gameViewListener.onImportScenario(result);
        }
    }

    /**
     * One footer action of the editor: the shared label plus the Alt mnemonic highlighted in 2D.
     */
    record FooterAction(String label, char mnemonic) {
    }

    /**
     * The editor footer in display order (issue #740). The labels are the shared {@link EditorText}
     * words, so the 2D and 3D clients cannot drift; static and package-visible so a test can pin
     * them without opening the modal window.
     */
    static List<FooterAction> editorFooterActions() {
        return List.of(new FooterAction(EditorText.EXPORT, 'E'),
                new FooterAction(EditorText.IMPORT, 'I'),
                new FooterAction(EditorText.RELOAD_FROM_GAME, 'R'),
                new FooterAction(EditorText.APPLY_PROGRAM, 'P'),
                new FooterAction(EditorText.REBUILD_WORLD, 'B'),
                new FooterAction(EditorText.CLOSE, 'C'));
    }

    @Override
    public void showIDE() {
        MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);
        gui.setTheme(centeredTitleTheme(gui.getTheme()));
        BasicWindow window = new BasicWindow();
        window.setTitle("LeTrain Editor " + letrain.BuildInfo.versionTag() + " (2D)");
        window.setHints(Arrays.asList(Window.Hint.CENTERED, Window.Hint.EXPANDED));

        Panel mainPanel = new Panel(new BorderLayout());

        // Three tabs share one editor: Scenario (seed + on build + on start), Programming
        // (program { ... }) and Config (configuration { ... }). The Scenario draft persists
        // between openings until the user hits Reload from game.
        final int tabScenario = 0;
        final int tabProgram = 1;
        final int tabConfig = 2;
        final int[] activeTab = {tabScenario};
        final String[] scenarioBuffer =
                {scenarioDraft != null ? scenarioDraft : gameViewListener.getScenarioText()};
        final String[] programBuffer =
                {letrain.command.ScenarioFile.programSection(gameViewListener.getProgram())};
        final String[] configBuffer =
                {configSectionOrEmpty(gameViewListener.getConfigurationText())};

        Panel tabBar = new Panel(new LinearLayout(Direction.HORIZONTAL));
        mainPanel.addComponent(tabBar, BorderLayout.Location.TOP);

        // Editor Area (the caret position is mirrored in the status bar below).
        final Label statusLabel = new Label("");
        final TextBox editor =
                new TextBox(new TerminalSize(60, 20), scenarioBuffer[0], TextBox.Style.MULTI_LINE) {
                    @Override
                    public com.googlecode.lanterna.gui2.Interactable.Result handleKeyStroke(
                            com.googlecode.lanterna.input.KeyStroke ks) {
                        com.googlecode.lanterna.gui2.Interactable.Result result =
                                super.handleKeyStroke(ks);
                        updateStatusLabel(statusLabel, this);
                        return result;
                    }
                };
        final Runnable refreshStatus = () -> updateStatusLabel(statusLabel, editor);
        refreshStatus.run();
        mainPanel.addComponent(editor, BorderLayout.Location.CENTER);

        // Side Panel (Reference)
        Panel sidePanel = new Panel(new LinearLayout(Direction.VERTICAL));
        sidePanel.addComponent(new Label("QUICK REFERENCE").setLabelWidth(30));

        ActionListBox refList = new ActionListBox(new TerminalSize(30, 30)) {
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
                        refreshStatus.run();
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
                letrain.command.GrammarReference.Group group =
                        activeTab[0] == tabProgram ? letrain.command.GrammarReference.Group.PROGRAM
                                : activeTab[0] == tabConfig
                                        ? letrain.command.GrammarReference.Group.CONFIG
                                        : letrain.command.GrammarReference.Group.BUILD;
                for (letrain.command.GrammarReference.Node rootNode : letrain.command.GrammarReference
                        .getReferenceTree(group)) {
                    build(rootNode, "");
                }
                if (selected >= 0 && selected < refList.getItems().size()) {
                    refList.setSelectedIndex(selected);
                }
            }
        };
        updateList.run();
        sidePanel.addComponent(refList);

        mainPanel.addComponent(sidePanel, BorderLayout.Location.RIGHT);

        // Footer: a fixed row with every action, independent of the active tab.
        final Panel footer = new Panel(new LinearLayout(Direction.HORIZONTAL));

        // Persists the editor content into the buffer of the active tab.
        final Runnable saveActive = () -> {
            if (activeTab[0] == tabProgram) {
                programBuffer[0] = editor.getText();
            } else if (activeTab[0] == tabConfig) {
                configBuffer[0] = editor.getText();
            } else {
                scenarioBuffer[0] = editor.getText();
                scenarioDraft = scenarioBuffer[0];
            }
        };
        final java.util.function.IntConsumer switchTo = tab -> {
            if (activeTab[0] == tab) {
                return;
            }
            saveActive.run();
            activeTab[0] = tab;
            if (tab == tabProgram) {
                editor.setText(programBuffer[0]);
            } else if (tab == tabConfig) {
                editor.setText(configBuffer[0]);
            } else {
                editor.setText(scenarioBuffer[0]);
            }
            updateList.run();
            refreshStatus.run();
            window.invalidate();
        };
        final Runnable switchToScenario = () -> switchTo.accept(tabScenario);
        final Runnable switchToProgram = () -> switchTo.accept(tabProgram);
        final Runnable switchToConfig = () -> switchTo.accept(tabConfig);

        // Moves the caret to a line of a given tab (used by the error picker).
        final java.util.function.BiConsumer<Integer, Integer> jumpTo = (tab, line) -> {
            switchTo.accept(tab);
            editor.setCaretPosition(Math.max(0, line - 1), 0);
            editor.takeFocus();
            refreshStatus.run();
            window.invalidate();
        };

        // Composes the full scenario (recipe + settings + program) from the three tab buffers.
        final java.util.function.Supplier<String> composeFull = () -> {
            saveActive.run();
            try {
                return letrain.command.ScenarioFile.compose(scenarioBuffer[0], configBuffer[0],
                        programBuffer[0]);
            } catch (Exception e) {
                return scenarioBuffer[0] + "\n" + configBuffer[0] + "\n" + programBuffer[0];
            }
        };

        final Runnable refreshAction = () -> {
            scenarioDraft = null;
            scenarioBuffer[0] = gameViewListener.getScenarioText();
            programBuffer[0] =
                    letrain.command.ScenarioFile.programSection(gameViewListener.getProgram());
            configBuffer[0] = gameViewListener.getConfigurationText();
            if (activeTab[0] == tabProgram) {
                editor.setText(programBuffer[0]);
            } else if (activeTab[0] == tabConfig) {
                editor.setText(configBuffer[0]);
            } else {
                editor.setText(scenarioBuffer[0]);
            }
            refreshStatus.run();
        };
        final Runnable exportAction = () -> {
            String full = composeFull.get();
            letrain.command.ScenarioCompiler.Result result =
                    letrain.command.ScenarioCompiler.compile(full);
            if (!result.ok() && !confirmExportAnyway(gui, diagnosticsText(result))) {
                return;
            }
            MultiWindowTextGUI fileGui = new MultiWindowTextGUI(screen);
            File file = new FileDialogBuilder().setTitle("Export Scenario")
                    .setDescription("Choose a file:")
                    .setActionLabel(LocalizedString.Save.toString()).build().showDialog(fileGui);
            if (file != null) {
                gameViewListener.onExportScenarioText(file, full);
            }
        };
        final Runnable importAction = () -> {
            MultiWindowTextGUI fileGui = new MultiWindowTextGUI(screen);
            File file = new FileDialogBuilder().setTitle("Open Scenario")
                    .setDescription("Choose a file:")
                    .setActionLabel(LocalizedString.Open.toString()).build().showDialog(fileGui);
            if (file == null) {
                return;
            }
            try {
                String fileText = java.nio.file.Files.readString(file.toPath());
                letrain.command.ScenarioFile.Parts parts =
                        letrain.command.ScenarioFile.split(fileText);
                scenarioDraft = parts.scenarioText();
                scenarioBuffer[0] = parts.scenarioText();
                configBuffer[0] = configSectionOrEmpty(parts.configurationText());
                programBuffer[0] = parts.programText();
                editor.setText(activeTab[0] == tabProgram ? programBuffer[0]
                        : activeTab[0] == tabConfig ? configBuffer[0] : scenarioBuffer[0]);
                refreshStatus.run();
            } catch (Exception ex) {
                com.googlecode.lanterna.gui2.dialogs.MessageDialog.showMessageDialog(gui,
                        "Scenario Error", String.valueOf(ex.getMessage()));
            }
        };
        final Runnable reprogramAction = () -> {
            saveActive.run();
            gameViewListener.onEditCommands(
                    letrain.command.ScenarioFile.programSectionBody(programBuffer[0]));
        };
        final Runnable rebuildAction = () -> {
            String full = composeFull.get();
            letrain.command.ScenarioCompiler.Result result =
                    letrain.command.ScenarioCompiler.compile(full);
            if (!result.ok()) {
                Jump jump = pickDiagnostic(gui, result, full);
                if (jump != null) {
                    jumpTo.accept(jump.tab(), jump.line());
                }
                return;
            }
            if (!confirmRebuildWorld(gui)) {
                return;
            }
            gameViewListener.onPlayScenarioText(full);
        };
        final Runnable cancelAction = () -> {
            saveActive.run();
            window.close();
        };

        // Fixed footer row: every action is always available, independent of the active tab. The
        // labels and mnemonics come from editorFooterActions() (the shared EditorText wording,
        // same words as the 3D HUD); the actions stay in the same order. Save/Load moved to the
        // File menu (Esc) in issue #743.
        final Runnable[] footerActions = {exportAction, importAction, refreshAction,
                reprogramAction, rebuildAction, cancelAction};
        List<FooterAction> footerButtons = editorFooterActions();
        for (int i = 0; i < footerButtons.size(); i++) {
            addFooterButton(footer, footerButtons.get(i).label(), footerButtons.get(i).mnemonic(),
                    footerActions[i]);
        }

        // Tabs glow their own colour when active (distinct from the focus style).
        com.googlecode.lanterna.gui2.InteractableRenderer<Button> tabRenderer =
                new com.googlecode.lanterna.gui2.InteractableRenderer<Button>() {
                    @Override
                    public com.googlecode.lanterna.TerminalSize getPreferredSize(Button component) {
                        return new com.googlecode.lanterna.TerminalSize(
                                component.getLabel().length() + 4, 1);
                    }

                    @Override
                    public void drawComponent(com.googlecode.lanterna.gui2.TextGUIGraphics graphics,
                            Button component) {
                        String label = component.getLabel();
                        int tab = EditorText.TAB_SCENARIO.equals(label) ? tabScenario
                                : EditorText.TAB_PROGRAMMING.equals(label) ? tabProgram : tabConfig;
                        if (tab == activeTab[0]) {
                            graphics.setForegroundColor(
                                    com.googlecode.lanterna.TextColor.ANSI.BLACK);
                            graphics.setBackgroundColor(
                                    com.googlecode.lanterna.TextColor.ANSI.CYAN);
                        } else if (component.isFocused()) {
                            graphics.applyThemeStyle(component.getThemeDefinition().getActive());
                        } else {
                            graphics.applyThemeStyle(component.getThemeDefinition().getNormal());
                        }
                        graphics.putString(0, 0, "< " + label + " >");
                        // Highlight the mnemonic letter (Alt+A / Alt+G / Alt+F) like the buttons.
                        char mnemonic = tab == tabScenario ? 'A' : tab == tabProgram ? 'G' : 'F';
                        int at = indexOfIgnoreCase(label, mnemonic);
                        if (at >= 0) {
                            if (tab == activeTab[0]) {
                                graphics.setForegroundColor(
                                        com.googlecode.lanterna.TextColor.ANSI.BLACK);
                                graphics.enableModifiers(com.googlecode.lanterna.SGR.BOLD);
                            } else {
                                graphics.setForegroundColor(
                                        com.googlecode.lanterna.TextColor.ANSI.RED_BRIGHT);
                            }
                            graphics.putString(2 + at, 0, label.substring(at, at + 1));
                            graphics.disableModifiers(com.googlecode.lanterna.SGR.BOLD);
                        }
                    }

                    @Override
                    public com.googlecode.lanterna.TerminalPosition getCursorLocation(
                            Button component) {
                        return null;
                    }
                };
        Button scenarioTabBtn = new Button(EditorText.TAB_SCENARIO, switchToScenario);
        scenarioTabBtn.setRenderer(tabRenderer);
        Button programTabBtn = new Button(EditorText.TAB_PROGRAMMING, switchToProgram);
        programTabBtn.setRenderer(tabRenderer);
        Button configTabBtn = new Button(EditorText.TAB_CONFIG, switchToConfig);
        configTabBtn.setRenderer(tabRenderer);
        tabBar.addComponent(scenarioTabBtn);
        tabBar.addComponent(programTabBtn);
        tabBar.addComponent(configTabBtn);
        window.addWindowListener(new com.googlecode.lanterna.gui2.WindowListenerAdapter() {
            @Override
            public void onInput(com.googlecode.lanterna.gui2.Window w,
                    com.googlecode.lanterna.input.KeyStroke ks,
                    java.util.concurrent.atomic.AtomicBoolean deliverEvent) {
                if (ks.getKeyType() == com.googlecode.lanterna.input.KeyType.Escape) {
                    cancelAction.run();
                    deliverEvent.set(false);
                    return;
                }
                if (ks.isAltDown() && ks.getCharacter() != null) {
                    char c = Character.toLowerCase(ks.getCharacter());
                    if (c == '1') {
                        switchToScenario.run();
                        deliverEvent.set(false);
                    } else if (c == '2') {
                        switchToProgram.run();
                        deliverEvent.set(false);
                    } else if (c == '3') {
                        switchToConfig.run();
                        deliverEvent.set(false);
                    } else if (c == 'a') {
                        switchToScenario.run();
                        deliverEvent.set(false);
                    } else if (c == 'g') {
                        switchToProgram.run();
                        deliverEvent.set(false);
                    } else if (c == 'f') {
                        switchToConfig.run();
                        deliverEvent.set(false);
                    } else if (c == 'q') {
                        refList.takeFocus();
                        deliverEvent.set(false);
                    } else if (c == 'e') {
                        exportAction.run();
                        deliverEvent.set(false);
                    } else if (c == 'i') {
                        importAction.run();
                        deliverEvent.set(false);
                    } else if (c == 'r') {
                        refreshAction.run();
                        deliverEvent.set(false);
                    } else if (c == 'p') {
                        reprogramAction.run();
                        deliverEvent.set(false);
                    } else if (c == 'b') {
                        rebuildAction.run();
                        deliverEvent.set(false);
                    } else if (c == 'c') {
                        cancelAction.run();
                        deliverEvent.set(false);
                    }
                }
            }
        });

        // Footer with the caret status line below it.
        Panel bottomPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        bottomPanel.addComponent(footer);
        bottomPanel.addComponent(statusLabel);
        mainPanel.addComponent(bottomPanel, BorderLayout.Location.BOTTOM);

        window.setComponent(mainPanel);
        // Start with the caret in the editor, not on the tabs/buttons.
        window.setFocusedInteractable(editor);
        gui.addWindowAndWait(window);
    }

    /** Mirrors the editor caret in the status bar as {@code Ln X, Col Y}. */
    private static void updateStatusLabel(Label label, TextBox editor) {
        TerminalPosition caret = editor.getCaretPosition();
        label.setText("Ln " + (caret.getRow() + 1) + ", Col " + (caret.getColumn() + 1));
    }

    /** A location in the editor: which tab and which 1-based line inside it. */
    record Jump(int tab, int line) {
    }

    /**
     * Shows the diagnostics in a scrollable list; returns the location picked with Enter, or
     * {@code null} if the dialog was just closed. Diagnostics refer to the composed scenario, so
     * the line is mapped back to the tab that owns the section.
     */
    private static Jump pickDiagnostic(MultiWindowTextGUI gui,
            letrain.command.ScenarioCompiler.Result result, String full) {
        final Jump[] picked = {null};
        BasicWindow dialog = new BasicWindow("Scenario errors");
        dialog.setHints(Arrays.asList(Window.Hint.CENTERED, Window.Hint.MODAL));

        Panel panel = new Panel(new LinearLayout(Direction.VERTICAL));
        panel.addComponent(new Label("Select an error and press Enter to jump to its line:"));
        ActionListBox list = new ActionListBox(new TerminalSize(60, 12));
        for (letrain.command.ScenarioCompiler.Diagnostic d : result.diagnostics()) {
            int line = d.line();
            list.addItem(d.line() + ":" + d.col() + ": " + d.message(), () -> {
                picked[0] = locateDiagnostic(full, line);
                dialog.close();
            });
        }
        panel.addComponent(list);
        panel.addComponent(new Button("Close", dialog::close));
        dialog.setComponent(panel);
        dialog.setFocusedInteractable(list);
        gui.addWindowAndWait(dialog);
        return picked[0];
    }

    /**
     * Maps a 1-based line of the composed scenario to the editor tab that owns it. The Scenario tab
     * omits the {@code configuration} section (it has its own tab), so lines after it shift up.
     */
    static Jump locateDiagnostic(String full, int line) {
        letrain.command.ScenarioFile.LineTarget target =
                letrain.command.ScenarioFile.locateLine(full, line);
        return new Jump(target.tab(), target.line());
    }

    /** Keeps the Config tab editable even when the world has no settings yet. */
    private static String configSectionOrEmpty(String section) {
        return section == null || section.isBlank() ? "configuration {\n}\n" : section;
    }

    /** Renders the diagnostics of a scenario check as one line per diagnostic. */
    private static String diagnosticsText(letrain.command.ScenarioCompiler.Result result) {
        // Keep the dialog on screen: show only the first few problems.
        final int maxLines = 8;
        StringBuilder sb = new StringBuilder();
        int total = result.diagnostics().size();
        int shown = 0;
        for (letrain.command.ScenarioCompiler.Diagnostic d : result.diagnostics()) {
            if (shown >= maxLines) {
                break;
            }
            if (shown > 0) {
                sb.append('\n');
            }
            sb.append(d.line()).append(':').append(d.col()).append(": ").append(d.message());
            shown++;
        }
        if (total > shown) {
            sb.append("\n... (").append(total - shown).append(" more)");
        }
        return sb.toString();
    }

    /** Asks before rebuilding the world: the replay discards the current world (issue #740). */
    private static boolean confirmRebuildWorld(MultiWindowTextGUI gui) {
        com.googlecode.lanterna.gui2.dialogs.MessageDialogButton choice =
                new com.googlecode.lanterna.gui2.dialogs.MessageDialogBuilder()
                        .setTitle(EditorText.REBUILD_CONFIRM_TITLE)
                        .setText(EditorText.REBUILD_CONFIRM_MESSAGE)
                        .addButton(com.googlecode.lanterna.gui2.dialogs.MessageDialogButton.Yes)
                        .addButton(com.googlecode.lanterna.gui2.dialogs.MessageDialogButton.No)
                        .build().showDialog(gui);
        return choice == com.googlecode.lanterna.gui2.dialogs.MessageDialogButton.Yes;
    }

    /** Asks whether to export a scenario that has syntax errors. */
    private static boolean confirmExportAnyway(MultiWindowTextGUI gui, String diagnostics) {
        com.googlecode.lanterna.gui2.dialogs.MessageDialogButton choice =
                new com.googlecode.lanterna.gui2.dialogs.MessageDialogBuilder()
                        .setTitle("Scenario has errors").setText(diagnostics + "\n\nExport anyway?")
                        .addButton(com.googlecode.lanterna.gui2.dialogs.MessageDialogButton.Yes)
                        .addButton(com.googlecode.lanterna.gui2.dialogs.MessageDialogButton.No)
                        .build().showDialog(gui);
        return choice == com.googlecode.lanterna.gui2.dialogs.MessageDialogButton.Yes;
    }

    /** Adds a footer button whose mnemonic letter is highlighted in the IDE style. */
    private static void addFooterButton(Panel footer, String label, char mnemonic,
            Runnable action) {
        Button button = new Button(label, action);
        button.setRenderer(mnemonicRenderer(mnemonic));
        footer.addComponent(button);
    }

    /**
     * A button renderer that highlights the mnemonic letter (red) inside {@code < label >}. Shared
     * with the other modal dialogs (the New Game dialog highlights its Alt access keys).
     */
    static com.googlecode.lanterna.gui2.InteractableRenderer<Button> mnemonicRenderer(
            char mnemonic) {
        return new com.googlecode.lanterna.gui2.InteractableRenderer<Button>() {
            @Override
            public com.googlecode.lanterna.TerminalSize getPreferredSize(Button component) {
                return new com.googlecode.lanterna.TerminalSize(component.getLabel().length() + 4,
                        1);
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
                if (!component.isEnabled()) {
                    graphics.setForegroundColor(com.googlecode.lanterna.TextColor.ANSI.BLACK);
                } else {
                    graphics.setForegroundColor(MNEMONIC_COLOR);
                }
                int at = indexOfIgnoreCase(label, mnemonic);
                if (at >= 0) {
                    graphics.putString(2 + at, 0, label.substring(at, at + 1));
                }
            }

            @Override
            public com.googlecode.lanterna.TerminalPosition getCursorLocation(Button component) {
                return null;
            }
        };
    }

    /** Case-insensitive index of {@code letter} in {@code text}, or -1. */
    private static int indexOfIgnoreCase(String text, char letter) {
        return text.toLowerCase().indexOf(Character.toLowerCase(letter));
    }

    /**
     * Wraps a theme so the window decoration centers its title (lanterna's {@code CENTER_TITLE}
     * property is false by default).
     */
    private static com.googlecode.lanterna.graphics.Theme centeredTitleTheme(
            com.googlecode.lanterna.graphics.Theme base) {
        return new com.googlecode.lanterna.graphics.DelegatingTheme(base) {
            @Override
            public com.googlecode.lanterna.graphics.ThemeDefinition getDefinition(Class<?> clazz) {
                com.googlecode.lanterna.graphics.ThemeDefinition definition =
                        super.getDefinition(clazz);
                if (clazz == com.googlecode.lanterna.gui2.DefaultWindowDecorationRenderer.class) {
                    return new com.googlecode.lanterna.graphics.DelegatingThemeDefinition(
                            definition) {
                        @Override
                        public boolean getBooleanProperty(String name, boolean defaultValue) {
                            return "CENTER_TITLE".equals(name)
                                    || super.getBooleanProperty(name, defaultValue);
                        }
                    };
                }
                return definition;
            }
        };
    }

    private void insertAtCaret(TextBox editor, String text) {
        String current = editor.getText();
        TerminalPosition pos = editor.getCaretPosition();

        // Snap to the beginning of the caret's line so we never split an existing line.
        String[] lines = current.split("\n", -1);
        int row = Math.min(pos.getRow(), lines.length - 1);
        int offset = 0;
        for (int i = 0; i < row; i++) {
            offset += lines[i].length() + 1; // +1 for the newline
        }

        String before = current.substring(0, offset);
        String after = current.substring(offset);
        String insertion = text + "\n"; // finish the line so the next insert starts clean
        String updated = before + insertion + after;
        editor.setText(updated);

        // Leave the caret on the line after the inserted snippet, ready to insert another.
        int caret = Math.min(offset + insertion.length(), updated.length());
        int newRow = 0;
        int col = 0;
        for (int i = 0; i < caret; i++) {
            if (updated.charAt(i) == '\n') {
                newRow++;
                col = 0;
            } else {
                col++;
            }
        }
        editor.setCaretPosition(newRow, col);
        editor.takeFocus();
    }

    public boolean isShowingOverlay() {
        return overlayMessage != null;
    }

    public void scrollOverlay(int amount) {
        overlayScroll += amount;
        if (overlayScroll < 0)
            overlayScroll = 0;
        paint();
    }

    /** Scrolls the overlay by one page (its visible content height). */
    public void scrollOverlayPage(int direction) {
        int rows = screen.getTerminalSize().getRows();
        int height = overlayMaximized ? rows - 2 : Math.min(25, rows - 2);
        int page = Math.max(1, height - 4);
        scrollOverlay(direction * page);
    }

    /** Grows (positive) or shrinks (negative) the overlay width in columns. */
    public void resizeOverlay(int deltaColumns) {
        overlayWidth = Math.max(20, overlayWidth + deltaColumns);
        overlayMaximized = false;
        overlayScroll = 0;
        paint();
    }

    /** Maximizes the overlay to fill the screen, or restores its previous width. */
    public void toggleOverlayMaximize() {
        overlayMaximized = !overlayMaximized;
        overlayScroll = 0;
        paint();
    }

    /** Word-wraps {@code lines} to {@code width} columns, hard-breaking words that don't fit. */
    static java.util.List<String> wrapLines(String[] lines, int width) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String line : lines) {
            if (line.length() <= width) {
                out.add(line);
                continue;
            }
            int start = 0;
            while (start < line.length()) {
                int end = Math.min(start + width, line.length());
                if (end < line.length()) {
                    int space = line.lastIndexOf(' ', end);
                    if (space > start) {
                        end = space;
                    }
                }
                out.add(line.substring(start, end));
                start = end;
                while (start < line.length() && line.charAt(start) == ' ') {
                    start++;
                }
            }
        }
        return out;
    }

    public boolean clearOverlay() {
        if (overlayMessage != null) {
            overlayMessage = null;
            overlayTitle = null;
            overlayScroll = 0;
            overlayMaximized = false;
            // Clear the screen right away to erase the overlay
            try {
                screen.clear();
            } catch (Exception e) {
            }
            return true;
        }
        return false;
    }

    /**
     * File menu ({@code Esc} in both clients), built from the shared {@link FileMenuText} rows so
     * the 2D terminal and the 3D HUD read the same words in the same order. New opens the New Game
     * dialog; at the boot menu (no game yet) Save and Settings are disabled with their reason (D8);
     * Settings opens the in-game settings dialog (S3b); Load/Save open their dialogs; Quit ends the
     * game. Esc closes the menu again (owner decision D11).
     */
    @Override
    public void showFileMenu() {
        MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);
        final boolean hasGame = gameViewListener.hasActiveGame();
        // No window title (#802): the menu is compact and the rows already say it all.
        BasicWindow window = new BasicWindow();
        window.setHints(Arrays.asList(Window.Hint.CENTERED));
        window.setCloseWindowWithEscape(true);
        Panel contentPanel = new Panel();
        contentPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));
        contentPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
        final Map<Character, Runnable> hotkeyActions = new java.util.HashMap<>();
        for (FileMenuText.Item item : fileMenuItems(hasGame)) {
            Runnable action = fileMenuAction(item, window);
            Button button = new Button(FileMenuText.displayLabel(item), action);
            button.setEnabled(item.enabled());
            // Alt mnemonic tinted like the rest of the dialogs (#803).
            button.setRenderer(mnemonicRenderer(FileMenuText.mnemonic(item.command())));
            if (item.enabled()) {
                hotkeyActions.put(Character.toLowerCase(FileMenuText.mnemonic(item.command())),
                        action);
            }
            contentPanel.addComponent(button);
        }
        window.setComponent(contentPanel);

        // #803: Alt+letter runs a row; at the boot menu (no game) Esc quits like the Quit row,
        // while
        // in-game Esc just closes the menu (D11).
        window.addWindowListener(new com.googlecode.lanterna.gui2.WindowListenerAdapter() {
            @Override
            public void onInput(Window w, KeyStroke keyStroke,
                    java.util.concurrent.atomic.AtomicBoolean deliverEvent) {
                if (keyStroke == null) {
                    return;
                }
                if (keyStroke.isAltDown() && keyStroke.getCharacter() != null) {
                    Runnable action =
                            hotkeyActions.get(Character.toLowerCase(keyStroke.getCharacter()));
                    if (action != null) {
                        action.run();
                        deliverEvent.set(false);
                        return;
                    }
                }
                if (keyStroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Escape
                        && !hasGame) {
                    setEndOfGame(true);
                    window.close();
                    deliverEvent.set(false);
                }
            }
        });
        gui.addWindowAndWait(window);
    }

    /** The rows the 2D menu shows for the current game state (boot menu vs in-game, D8). */
    static List<FileMenuText.Item> fileMenuItems(boolean hasGame) {
        return FileMenuText.items(FileMenuText.MenuState.terminal(hasGame));
    }

    /** Action of one File menu row; locked rows stay disabled and never run it. */
    private Runnable fileMenuAction(FileMenuText.Item item, Window window) {
        switch (item.command()) {
            case NEW:
                return () -> {
                    window.close();
                    gameViewListener.onNewGame();
                };
            case LOAD:
                return () -> {
                    showLoadDialog();
                    window.close();
                };
            case SAVE:
                return () -> {
                    showSaveDialog();
                    window.close();
                };
            case QUIT:
                return () -> {
                    setEndOfGame(true);
                    window.close();
                };
            case SETTINGS:
                return () -> {
                    window.close();
                    gameViewListener.onSettings();
                };
            default:
                return () -> {
                };
        }
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
        boolean terminalClosed = false;
        if (terminal != null) {
            try {
                // Lanterna saves the tty mode on construction and restores it here (and in its own
                // shutdown hook), so it owns the terminal-state handback.
                terminal.close();
                terminalClosed = true;
            } catch (Exception e) {
                // Ignore
            }
        }
        if (!terminalClosed) {
            // Lanterna could not hand the terminal back (no terminal object, or close failed):
            // best-effort recovery so the shell is not left in raw mode (no echo).
            fallbackSttySane();
        }
    }

    /** Last-resort tty recovery, only for when Lanterna could not close the terminal itself. */
    private void fallbackSttySane() {
        try {
            System.out.print("\033[0m\033[?1049l\033[?25h");
            System.out.flush();
        } catch (Exception ignored) {
            // Ignore
        }
        try {
            Process p =
                    Runtime.getRuntime().exec(new String[] {"sh", "-c", "stty sane < /dev/tty"});
            if (!p.waitFor(2, TimeUnit.SECONDS)) {
                p.destroyForcibly();
            }
        } catch (Exception ignored) {
            // Not a Unix tty (e.g. Windows/Swing): nothing to restore here
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
        drawCommandLine(text, error, null);
    }

    @Override
    public void drawCommandLine(String text, String error, String notice) {
        if (screen == null) {
            return;
        }
        drawCommandLine(screen.newTextGraphics(), screen.getTerminalSize(), text, error, notice);
    }

    /**
     * Paints the command bar: the typed text plus its short feedback. The error is red and the
     * notice (D1 contextual channel) yellow, both on the last screen row. Static and
     * package-visible so tests can drive it with a mocked graphics.
     */
    static void drawCommandLine(TextGraphics g, TerminalSize size, String text, String error,
            String notice) {
        int screenRows = size.getRows();
        int screenCols = size.getColumns();
        if (screenRows < 2)
            return; // safety

        int drawY = screenRows - 1; // Last line of the absolute screen

        g.setBackgroundColor(TextColor.ANSI.BLACK);
        g.setForegroundColor(TextColor.ANSI.WHITE);
        g.putString(0, drawY, " ".repeat(screenCols)); // clear line

        String prompt = ":" + text + "_";
        g.putString(0, drawY, prompt);

        int cursorX = prompt.length() + 2; // small gap after the prompt
        if (error != null && !error.isEmpty()) {
            String errStr = " " + error.replace('\n', ' ').replace('\r', ' ') + " ";
            if (cursorX < screenCols) {
                // Truncate if it overflows
                if (cursorX + errStr.length() > screenCols) {
                    errStr = errStr.substring(0, screenCols - cursorX);
                }
                g.setBackgroundColor(TextColor.ANSI.RED);
                g.setForegroundColor(TextColor.ANSI.WHITE);
                g.putString(cursorX, drawY, errStr);
                cursorX += errStr.length() + 1;
            }
        }

        if (notice != null && !notice.isEmpty()) {
            String noticeStr = " " + notice.replace('\n', ' ').replace('\r', ' ') + " ";
            if (cursorX < screenCols) {
                if (cursorX + noticeStr.length() > screenCols) {
                    noticeStr = noticeStr.substring(0, screenCols - cursorX);
                }
                g.setBackgroundColor(TextColor.ANSI.YELLOW);
                g.setForegroundColor(TextColor.ANSI.BLACK);
                g.putString(cursorX, drawY, noticeStr);
            }
        }
    }

}
