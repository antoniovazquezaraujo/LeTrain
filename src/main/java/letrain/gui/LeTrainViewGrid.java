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
    public static final int ROWS = 30;
    public static final int COLS = 50;
    private Point mapScrollPage = new Point(0, 0);
    final int TEXT_SIZE = 20;
    Font font = Font.font("Monospace", TEXT_SIZE);
    Text[][] texts = new Text[COLS][ROWS];
    TextFlow flow = new TextFlow();
    Text statusBar = new Text();
    public LeTrainViewGrid() {
        setStyle("-fx-background-color: black;");
        setPrefHeight(300);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                texts[col][row] = new Text(" ");
                texts[col][row].setLineSpacing(0);
                texts[col][row].setBoundsType(TextBoundsType.VISUAL);
                texts[col][row].setFont(font);
                flow.getChildren().add(texts[col][row]);
            }
            flow.getChildren().add(new Text(System.lineSeparator()));
        }
        setCenter(flow);
        statusBar.setFill(Color.WHITE);
        setBottom(statusBar);
    }

    @Override
    public Point getMapScrollPage() {
        return this.mapScrollPage;
    }

    @Override
    public void setMapScrollPage(Point pos) {
        statusBar.setText("Página: "+ mapScrollPage.getY()+ "," + mapScrollPage.getX());
        this.mapScrollPage = pos;
    }


    @Override
    public void paint() {

    }

    @Override
    public void clear() {
        for (int col = 0; col < COLS; col++) {
            for (int row = 0; row < ROWS; row++) {
                texts[col][row].setText(" ");
            }
        }
    }

    @Override
    public void set(int x, int y, String c) {
        x -= mapScrollPage.getX() * COLS;
        y -= mapScrollPage.getY() * ROWS;
        if (x >= 0 && x < COLS && y >= 0 && y < ROWS) {
            texts[x][y].setText(c);
        }
    }

    @Override
    public void setColor(int x, int y, Color color) {
        x -= mapScrollPage.getX() * COLS;
        y -= mapScrollPage.getY() * ROWS;
        if (x >= 0 && x < COLS && y >= 0 && y < ROWS) {
            texts[x][y].setFill(color);
        }
    }

    @Override
    public void setPageOfPos(int x, int y) {
        if(x<0)x-=COLS;
        if(y<0)y-=ROWS;
        int pageX = x / COLS;
        int pageY = y / ROWS;
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

}