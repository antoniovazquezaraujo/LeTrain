package letrain.mvp.impl;

import com.sun.javafx.tk.FontMetrics;
import com.sun.javafx.tk.Toolkit;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import letrain.map.Point;
import letrain.mvp.View;

public class ViewGrid extends BorderPane implements View {
    private static final int DEFAULT_ROWS = 40;
    private static final int DEFAULT_COLS = 100;
    int cols = DEFAULT_COLS;
    int rows = DEFAULT_ROWS;
    private Point mapScrollPage = new Point(0, 0);
    private final Canvas canvas;
    private final GraphicsContext gc;
    private static final int TEXT_SIZE = 10;
    private static Font font = Font.font("Monospace", TEXT_SIZE);
    private int zoom = 0;
    private float charWidth;
    private float charHeight;

    public ViewGrid() {
        setStyle("-fx-background-color: black;");
        FontMetrics metrics = Toolkit.getToolkit().getFontLoader().getFontMetrics(font);
        charWidth = metrics.computeStringWidth("X");
        charHeight = metrics.getLineHeight();
        canvas = new Canvas(cols * charWidth, rows * charHeight);
        setCenter(canvas);
        gc = canvas.getGraphicsContext2D();
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(font);

    }

    void recalcFontSize() {
        this.font = Font.font("Monospace", TEXT_SIZE + zoom);
        FontMetrics metrics = Toolkit.getToolkit().getFontLoader().getFontMetrics(font);
        charWidth = metrics.computeStringWidth("X");
        charHeight = metrics.getLineHeight();
        double height = getHeight();
        double width = getWidth();
        cols = (int) (width / charWidth);
        rows = (int) (height / charHeight);
        canvas.setWidth(cols * charWidth);
        canvas.setHeight(rows * charHeight);
        gc.setFont(this.font);

    }

    @Override
    public Point getMapScrollPage() {
        return this.mapScrollPage;
    }

    @Override
    public void setMapScrollPage(Point pos) {
        this.mapScrollPage = pos;
    }

    @Override
    public void paint() {

    }

    @Override
    public void clear() {
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, getWidth(), getHeight());
    }

    @Override
    public void set(int x, int y, String c) {
        x -= mapScrollPage.getX() * cols;
        y -= mapScrollPage.getY() * rows;
        if (x >= 0 && x < cols && y >= 0 && y < rows) {

            // TODO probar diferentes grosores para diferentes elementos???
            gc.setLineWidth(1);

            gc.strokeText(c, x * charWidth, y * charHeight);
        }
    }

    @Override
    public void setColor(Color color) {
        gc.setStroke(color);
    }

    @Override
    public void setPageOfPos(int x, int y) {
        if (x < 0)
            x -= cols;
        if (y < 0)
            y -= rows;
        int pageX = x / cols;
        int pageY = y / rows;
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
    public void setStatusBarText(String info) {

    }

    @Override
    public void setInfoBarText(String info) {

    }

    @Override
    public void incZoom() {
        this.zoom++;
        recalcFontSize();
    }

    @Override
    public void decZoom() {
        this.zoom--;
        recalcFontSize();
    }

    @Override
    public void resetZoom() {
        this.zoom = 0;
        recalcFontSize();
    }

}