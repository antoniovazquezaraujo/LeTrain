package letrain.gui;

import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;
import javafx.scene.text.TextFlow;
import letrain.map.Point;
import letrain.tui.SimpleUI;

public class LeTrainViewGrid extends BorderPane implements SimpleUI {
    public static final int ROWS = 50;
    public static final int COLS = 50;
    private Point position = new Point(0, 0); // scroll position of the viewer
    final int TEXT_SIZE = 20;
    Font font = Font.font("Monospace", TEXT_SIZE);
    Text[][] texts = new Text[ROWS][COLS];
    TextFlow flow = new TextFlow();

    public LeTrainViewGrid() {
        setPrefHeight(400);
        for (int m = 0; m < COLS; m++) {
            for (int n = 0; n < ROWS; n++) {
                HBox box = new HBox();
                texts[n][m] = new Text(" ");
                texts[n][m].setLineSpacing(0);
                texts[n][m].setBoundsType(TextBoundsType.VISUAL);
                texts[n][m].setFont(font);
                flow.getChildren().add(texts[n][m]);
            }
            flow.getChildren().add(new Text(System.lineSeparator()));
        }
        setCenter(flow);
    }

    @Override
    public Point getPos() {
        return this.position;
    }

    @Override
    public void setPos(Point pos) {
//        statusBar.setText("Página: "+ position.getY()+ "," + position.getX());
        this.position = pos;
    }


    @Override
    public void paint() {

    }

    @Override
    public void clear() {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                texts[col][row].setText(" ");
            }
        }
    }

    @Override
    public void set(int x, int y, String c) {
        x -= position.getX() * COLS;
        y -= position.getY() * ROWS;
        if (x >= 0 && x < COLS && y >= 0 && y < ROWS) {
            texts[x][y].setText(c);
        }
    }

    @Override
    public void setColor(int x, int y, Color color) {
        x -= position.getX() * COLS;
        y -= position.getY() * ROWS;
        if (x >= 0 && x < COLS && y >= 0 && y < ROWS) {
            texts[x][y].setFill(color);
        }
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

}