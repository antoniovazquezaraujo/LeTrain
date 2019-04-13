package letrain.gui;

import javafx.event.EventHandler;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import letrain.tui.SimpleUI;

public class Gui extends BorderPane implements SimpleUI {
    public static final int ROWS = 100;
    public static final int COLS = 100;
    char[][] screen = new char[COLS][ROWS];
    TextArea textArea;
    TextField textField;

    public Gui() {
        setPrefSize(800,800);
        textArea = new TextArea();
        textArea.setFocusTraversable(false);
        textField = new TextField();
        textArea.setFont(Font.font ("Monospace", 20));
        setCenter(textArea);
        setBottom(textField);
        clear();
        textField.requestFocus();
    }

    public void addListener(EventHandler<KeyEvent> keyEventEventHandler) {
        textField.addEventHandler(KeyEvent.KEY_PRESSED, keyEventEventHandler);
    }

    @Override
    public void paint() {
        StringBuffer sb = new StringBuffer();
        textArea.clear();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                sb.append(screen[col][row]);
            }
            textArea.appendText(sb.toString() + System.lineSeparator());
            sb.setLength(0);
        }
    }

    @Override
    public void clear() {
        textArea.clear();
        for (int col = 0; col < COLS; col++) {
            for (int row = 0; row < ROWS; row++) {
                screen[col][row] = ' ';
            }
        }
    }

    @Override
    public void set(int x, int y, char c) {
        screen[x][y] = c;
    }

    @Override
    public void clear(int x, int y) {
        screen[x][y] = '#';
    }

    @Override
    public void fill(int x, int y, int width, int height, char c) {
        for (int col = x; col < x + width; col++) {
            for (int row = y; row < y + height; row++) {
                set(col, row, c);
            }
        }
    }

    @Override
    public void box(int x, int y, int width, int height) {
        fill(x, y, width, 1, (char) '-');
        fill(x, y + height, width, 1, (char) '-');
        fill(x, y, 1, height, (char) '|');
        fill(x + width, y, 1, height, (char) '|');
        set(x, y, '+');
        set(x, y + height, '+');
        set(x + width, y, '+');
        set(x + width, y + height, '+');
    }
}
