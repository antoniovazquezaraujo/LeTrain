package letrain.mvp.impl;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.lanterna.Symbols;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.TextColor.ANSI;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import letrain.map.Point;
import letrain.mvp.GameViewListener;

public class View implements letrain.mvp.View {
    Logger log = LoggerFactory.getLogger(View.class);
    private final GameViewListener gameViewListener;
    private Point mapScrollPage = new Point(0, 0);
    private Screen screen;
    private DefaultTerminalFactory terminalFactory;
    private Terminal terminal;
    private TerminalSize terminalSize;
    private TextGraphics centralGraphics;
    private TerminalPosition centralGraphicsPosition;
    private TerminalSize centralGraphicsSize;
    private TextGraphics bottomGraphics;
    private TerminalPosition bottonGraphicsPosition;
    private TerminalSize bottonGraphicsSize;

    private TextColor fgColor;
    private TextColor bgColor;

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

        centralGraphics = screen.newTextGraphics();
        bottomGraphics = screen.newTextGraphics();
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
        if (keyStroke != null &&
                (keyStroke.getKeyType() == KeyType.Escape
                        || keyStroke.getKeyType() == KeyType.EOF)) {
            return true;
        }
        return false;
    }

    @Override
    public Point getMapScrollPage() {
        return this.mapScrollPage;
    }

    @Override
    public void setMapScrollPage(Point pos) {
        this.mapScrollPage = pos;
        setStatusBarText("Page: " + mapScrollPage.getX() + ", " + mapScrollPage.getY());
    }

    public void setStatusBarText(String text) {
        // statusBar.setText(text);
    }

    public void setInfoBarText(String text) {
        bottomGraphics.putString(bottonGraphicsPosition.withRelative(1, 3), text);
    }

    // Método que recibe una lista de cadenas y pinta en bottomGraphics todas las
    // opciones pero
    // con un color de fondo diferente la opción que va entre corchetes
    public void paintOptions(String[] options) {
        // If the option contains "[", it means that it is a selected option
        // We need to paint it yellow
        int length = 0;
        for (String option : options) {
            if (option.contains("[")) {
                TextColor oldColor = bgColor;
                bottomGraphics.setBackgroundColor(TextColor.ANSI.YELLOW);
                bottomGraphics.putString(bottonGraphicsPosition.withRelative(length, 1), option);
                bottomGraphics.setBackgroundColor(oldColor);
            } else {
                bottomGraphics.putString(bottonGraphicsPosition.withRelative(length, 1), option);
            }
            length += option.length() + 1;
        }
    }

    public void setHelpBarText(String text) {
        String[] lines = text.split("\n");
        paintOptions(lines[0].split(" "));
        bottomGraphics.putString(bottonGraphicsPosition.withRelative(1, 2), lines[1]);
    }

    @Override
    public void paint() {
        TerminalSize changedSize = screen.doResizeIfNecessary();
        if (changedSize != null) {
            terminalSize = changedSize;
            recalculateSizes(terminalSize);
            centralGraphics.fillRectangle(centralGraphicsPosition, centralGraphicsSize, ' ');
        }
        // drawBox(bottomGraphics, bottonGraphicsPosition, bottonGraphicsSize);
        // draw a line ober botomGraphics
        bottomGraphics.drawLine(bottonGraphicsPosition.withRelative(1, 0),
                bottonGraphicsPosition.withRelative(bottonGraphicsSize.getColumns() - 2, 0),
                TextCharacter.fromCharacter(Symbols.SINGLE_LINE_HORIZONTAL, fgColor, bgColor)[0]);

        try {
            this.screen.refresh();
            Thread.yield();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getCols() {
        return centralGraphicsSize.getColumns();
    }

    public int getRows() {
        return centralGraphicsSize.getRows() - 2;
    }

    public void set(int x, int y, String c) {
        x -= mapScrollPage.getX() * getCols();
        y -= mapScrollPage.getY() * getRows();
        if (x >= 0 && x < getCols() && y >= 0 && y < getRows()) {
            for (int i = 0; i < c.length(); i++) {
                centralGraphics.setCharacter(x + i, y,
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
        setMapScrollPage(new Point(pageX, pageY));
    }

    @Override
    public void clear(int x, int y) {
        bottomGraphics.setBackgroundColor(bgColor);
        centralGraphics.setBackgroundColor(bgColor);
        bottomGraphics.setForegroundColor(fgColor);
        centralGraphics.setForegroundColor(fgColor);
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
        bottomGraphics.setBackgroundColor(bgColor);
        centralGraphics.setBackgroundColor(bgColor);
        bottomGraphics.setForegroundColor(fgColor);
        centralGraphics.setForegroundColor(fgColor);
        bottomGraphics.fillRectangle(bottonGraphicsPosition, bottonGraphicsSize, ' ');
        centralGraphics.fillRectangle(centralGraphicsPosition, centralGraphicsSize, ' ');
    }

    void recalculateSizes(TerminalSize terminalSize) {
        centralGraphicsSize = new TerminalSize(terminalSize.getColumns(), terminalSize.getRows() - 2);
        centralGraphicsPosition = TerminalPosition.TOP_LEFT_CORNER;
        bottonGraphicsSize = new TerminalSize(terminalSize.getColumns(), 4);
        bottonGraphicsPosition = new TerminalPosition(0, terminalSize.getRows() - 4);
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
}
