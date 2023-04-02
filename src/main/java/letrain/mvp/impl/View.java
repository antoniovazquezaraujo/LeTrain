package letrain.mvp.impl;

import java.io.IOException;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import letrain.map.Point;
import letrain.mvp.Model;
import letrain.mvp.GameViewListener;


public class View implements letrain.mvp.View {
    private final GameViewListener gameViewListener;
    private Point position = new Point(0, 0); // scroll position of the viewer
    private Point mapScrollPage = new Point(0, 0);
    private TextGraphics statusBar;
    private TextGraphics infoBar;
    private TextGraphics helpBar;
    private TextGraphics mainGraphics;
    private Screen screen;
    static TerminalSize terminalSize;
    private TextColor color;

    public View(GameViewListener gameViewListener) {
        this.gameViewListener = gameViewListener;
     
        // statusBar = new Text();
        // infoBar = new Text();
        // helpBar = new Text();

        // setBottom(bottomBox);
        // this.setFocusTraversable(true);
        // clear();
        // addEventListener();
        // this.requestFocus();
    }

    public void setScreen(Screen screen){
        this.screen = screen;
        resizePartsIfNecessary();
    }
    public int getRows(){
        return this.screen.getTerminalSize().getRows()-3;
    }
    public int getCols(){
        return this.screen.getTerminalSize().getColumns();
    }
    public KeyStroke readKey() {
        try {
            KeyStroke input = screen.pollInput();
            if(input != null){
                resizePartsIfNecessary();
                return input;
            }
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
        this.screen.newTextGraphics().putString(1,getRows()-1,  text);
    }

    public void setInfoBarText(String text) {
        this.screen.newTextGraphics().putString(1,getRows()-2,  text);
    }

    public void setHelpBarText(String text) {
        this.screen.newTextGraphics().putString(1,getRows()-3,  text);
    }

    @Override
    public void paint() {
        try {
            this.screen.refresh();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void resizePartsIfNecessary() {
        TerminalSize newSize = this.screen.doResizeIfNecessary();
        if (newSize != null) {
            terminalSize = newSize;
            // labelTextGraphics = screen.newTextGraphics();
            // lineTextGraphics = screen.newTextGraphics();
            // TerminalPosition upLeft = new TerminalPosition(1, 1);
            // baseTextGraphics = baseTextGraphics.newTextGraphics(upLeft,
            // terminalSize.withRelativeRows(terminalSize.getRows()));
        }
    }

    //////////////////////////////////////////
    public void set(int x, int y, String c) {
        x -= mapScrollPage.getX() * getCols();
        y -= mapScrollPage.getY() * getRows();
        if (x >= 0 && x < getCols() && y >= 0 && y < getRows()) {
            screen.newTextGraphics()
                    .setForegroundColor(this.color)
                    .putString(x, y, c);
        }
    }

    @Override
    public void setColor(TextColor color) {
        this.color = color;
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
        this.screen.clear();
    }
}
