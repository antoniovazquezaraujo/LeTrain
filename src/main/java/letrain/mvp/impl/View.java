package letrain.mvp.impl;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import com.googlecode.lanterna.Symbols;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.TextColor.ANSI;
import com.googlecode.lanterna.bundle.LanternaThemes;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.LocalizedString;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.dialogs.FileDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialogBuilder;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import letrain.map.Point;
import letrain.mvp.GameViewListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class View implements letrain.mvp.View {
    Logger log = LoggerFactory.getLogger(View.class);
    private final GameViewListener gameViewListener;
    private Point mapScrollPage = new Point(0, 0);
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

    public View(GameViewListener gameViewListener) {
        this.gameViewListener = gameViewListener;
        terminalFactory = new DefaultTerminalFactory();
        try {
            terminal = terminalFactory.createTerminal();
            terminal.setCursorVisible(false);
            setScreen(createScreen(terminal));
        } catch (IOException e) {
            e.printStackTrace();
        }
        terminalSize = screen.getTerminalSize();
        gameBox = screen.newTextGraphics();
        menuBox = screen.newTextGraphics();
        this.fgColor = ANSI.WHITE;
        this.bgColor = ANSI.BLACK;

        recalculateSizes(terminalSize);
    }

    public void setScreen(Screen screen) {
        this.screen = screen;
    }

    public KeyStroke readKey() {
        try {
            return screen.pollInput();
        } catch (IOException e) {
        }
        return null;
    }

    public boolean isEndOfGame(KeyStroke keyStroke) {
        return endOfGame;
    }

    @Override
    public Point getMapScrollPage() {
        return this.mapScrollPage;
    }

    @Override
    public void setMapScrollPage(Point pos) {
        this.mapScrollPage = pos;
        setStatusBarText("Page: " + mapScrollPage.getX() + ", " + mapScrollPage.getY());
        View.this.gameViewListener.onMapPageChanged(mapScrollPage, gameBoxSize.getColumns(), gameBoxSize.getRows());
    }

    public void setStatusBarText(String text) {
        // statusBar.setText(text);
    }

    @Override
    public void setInfoBarText(String text) {
        menuBox.putString(menuBoxPosition.withRelative(1, 3), text);
    }

    @Override
    public void paint() {
        TerminalSize changedSize = screen.doResizeIfNecessary();
        if (changedSize != null) {
            terminalSize = changedSize;
            recalculateSizes(terminalSize);
            View.this.gameViewListener.onMapPageChanged(mapScrollPage, gameBoxSize.getColumns(),
                    gameBoxSize.getRows());
            gameBox.fillRectangle(gameBoxPosition, gameBoxSize, ' ');
        }

        try {
            this.screen.refresh();
            Thread.yield();
        } catch (IOException e) {
            e.printStackTrace();
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
    public void setMenu(String[] options, int selectedOption) {
        int length = 1;
        TextColor oldBgColor = bgColor;
        for (int option = 0; option < options.length; option++) {
            String[] parts = options[option].split("&");
            String firstPart = parts[0];
            String secondPart = parts[1].substring(0, 1);
            String thirdPart = parts[1].substring(1);
            TextColor oldFgColor = fgColor;
            if (option == selectedOption) {
                bgColor = ANSI.BLUE;
                menuBox.setBackgroundColor(bgColor);
            } else {
                bgColor = oldBgColor;
                menuBox.setBackgroundColor(bgColor);
            }
            menuBox.setForegroundColor(oldFgColor);
            menuBox.putString(menuBoxPosition.withRelative(length, 1), firstPart);
            menuBox.setForegroundColor(TextColor.ANSI.YELLOW);
            length += firstPart.length();
            menuBox.putString(menuBoxPosition.withRelative(length, 1), secondPart);
            menuBox.setForegroundColor(oldFgColor);
            length += secondPart.length();
            menuBox.putString(menuBoxPosition.withRelative(length, 1), thirdPart);
            menuBox.setForegroundColor(oldFgColor);
            length += thirdPart.length() + 1;
            menuBox.setBackgroundColor(oldBgColor);
            bgColor = oldBgColor;
            fgColor = oldFgColor;
        }

    }

    @Override
    public void setHelpBarText(String text) {
        menuBox.putString(menuBoxPosition.withRelative(1, 2), text);
    }

    public void set(int x, int y, String c) {
        x -= mapScrollPage.getX() * getCols();
        y -= mapScrollPage.getY() * getRows();
        if (x >= 0 && x < getCols() && y >= 0 && y < getRows()) {
            for (int i = 0; i < c.length(); i++) {
                gameBox.setCharacter(x + i, y,
                        TextCharacter.fromCharacter(c.charAt(i), this.fgColor, bgColor)[0]);
            }
        }
    }

    @Override
    public TextColor getFgColor() {
        return fgColor;
    }

    @Override
    public void setFgColor(TextColor color) {
        this.fgColor = color;
    }

    @Override
    public void setBgColor(TextColor color) {
        this.bgColor = color;
    }

    @Override
    public void setPageOfPos(int x, int y) {
        if (x < 0)
            x -= getCols();
        if (y < 0)
            y -= getRows();
        int pageX = x / getCols();
        int pageY = y / getRows();
        Point actualPage = getMapScrollPage();
        if (pageX != actualPage.getX() || pageY != actualPage.getY()) {
            setMapScrollPage(new Point(pageX, pageY));
        }
    }

    @Override
    public void clear(int x, int y) {
        menuBox.setBackgroundColor(bgColor);
        gameBox.setBackgroundColor(bgColor);
        menuBox.setForegroundColor(fgColor);
        gameBox.setForegroundColor(fgColor);
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
        menuBox.setBackgroundColor(bgColor);
        gameBox.setBackgroundColor(bgColor);
        menuBox.setForegroundColor(fgColor);
        gameBox.setForegroundColor(fgColor);
        menuBox.fillRectangle(menuBoxPosition, menuBoxSize, ' ');
        gameBox.fillRectangle(gameBoxPosition, gameBoxSize, ' ');
    }

    void recalculateSizes(TerminalSize terminalSize) {
        gameBoxSize = new TerminalSize(terminalSize.getColumns(), terminalSize.getRows() - 4);
        gameBoxPosition = TerminalPosition.TOP_LEFT_CORNER;
        menuBoxSize = new TerminalSize(terminalSize.getColumns(), 4);
        menuBoxPosition = new TerminalPosition(0, terminalSize.getRows() - 4);
    }

    Screen createScreen(Terminal terminal) throws IOException {
        Screen screen;
        screen = new TerminalScreen(terminal);
        screen.startScreen();
        screen.setCursorPosition(null);
        return screen;
    }

    public static void drawBox(TextGraphics textGraphics, TerminalPosition topLeft, TerminalSize size) {
        TerminalPosition topRight = topLeft.withRelativeColumn(size.getColumns() - 1);
        TerminalPosition bottomLeft = topLeft.withRelativeRow(size.getRows() - 1);
        TerminalPosition bottomRight = bottomLeft.withRelativeColumn(size.getColumns() - 1);

        textGraphics.drawLine(topLeft.withRelative(1, 0), topRight.withRelative(-1, 0), Symbols.SINGLE_LINE_HORIZONTAL);
        textGraphics.drawLine(bottomLeft.withRelative(1, 0), bottomRight.withRelative(-1, 0),
                Symbols.SINGLE_LINE_HORIZONTAL);
        textGraphics.drawLine(topLeft.withRelative(0, 1), bottomLeft.withRelative(0, -1), Symbols.SINGLE_LINE_VERTICAL);
        textGraphics.drawLine(topRight.withRelative(0, 1), bottomRight.withRelative(0, -1),
                Symbols.SINGLE_LINE_VERTICAL);

        textGraphics.setCharacter(topLeft, Symbols.SINGLE_LINE_TOP_LEFT_CORNER);
        textGraphics.setCharacter(topRight, Symbols.SINGLE_LINE_TOP_RIGHT_CORNER);
        textGraphics.setCharacter(bottomLeft, Symbols.SINGLE_LINE_BOTTOM_LEFT_CORNER);
        textGraphics.setCharacter(bottomRight, Symbols.SINGLE_LINE_BOTTOM_RIGHT_CORNER);
    }

    @Override
    public void showMainDialog() {
        MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);
        String theme = "businessmachine";
        if (theme != null) {
            gui.setTheme(LanternaThemes.getRegisteredTheme(theme));
        }
        BasicWindow window = new BasicWindow();
        window.setHints(Arrays.asList(Window.Hint.CENTERED));
        window.setTitle("LeTrain");
        Panel contentPanel = new Panel();
        contentPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));
        contentPanel.addComponent(new Label("Menu"));
        contentPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));
        contentPanel.addComponent(new Button("New Game", new Runnable() {
            @Override
            public void run() {
                View.this.gameViewListener.onNewGame();
                window.close();
            }
        }));
        contentPanel.addComponent(new Button("Save Game", new Runnable() {
            @Override
            public void run() {
                File result = new FileDialogBuilder()
                        .setTitle("Save File")
                        .setDescription("Choose a file:")
                        .setActionLabel(LocalizedString.Save.toString())
                        .build()
                        .showDialog(gui);
                View.this.gameViewListener.onSaveGame(result);
                window.close();
            }
        }));
        contentPanel.addComponent(new Button("Load Game", new Runnable() {
            @Override
            public void run() {
                File result = new FileDialogBuilder()
                        .setTitle("Open File")
                        .setDescription("Choose a file:")
                        .setActionLabel(LocalizedString.Open.toString())
                        .build()
                        .showDialog(gui);
                View.this.gameViewListener.onLoadGame(result);
                window.close();
            }
        }));
        contentPanel.addComponent(new Button("Save commands", new Runnable() {
            @Override
            public void run() {
                File result = new FileDialogBuilder()
                        .setTitle("Save File")
                        .setDescription("Choose a file:")
                        .setActionLabel(LocalizedString.Save.toString())
                        .build()
                        .showDialog(gui);
                View.this.gameViewListener.onSaveCommands(result);
                window.close();
            }
        }));
        contentPanel.addComponent(new Button("Load commands", new Runnable() {
            @Override
            public void run() {
                File result = new FileDialogBuilder()
                        .setTitle("Open File")
                        .setDescription("Choose a file:")
                        .setActionLabel(LocalizedString.Open.toString())
                        .build()
                        .showDialog(gui);
                View.this.gameViewListener.onLoadCommands(result);
                window.close();
            }
        }));
        contentPanel.addComponent(new Button("Edit commands", new Runnable() {
            @Override
            public void run() {
                String result = new TextInputDialogBuilder()
                        .setTitle("LeTrain program editor")
                        .setTextBoxSize(new TerminalSize(85, 25))
                        .setInitialContent(gameViewListener.getProgram())
                        .build()
                        .showDialog(gui);
                if (result != null) {
                    View.this.gameViewListener.onEditCommands(result);
                }
                window.close();
            }
        }));

        contentPanel.addComponent(new Button("Exit game", new Runnable() {
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
        this.endOfGame = endOfGame;
    }
}
