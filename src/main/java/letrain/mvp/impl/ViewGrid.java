package letrain.mvp.impl;

import com.sun.javafx.tk.FontMetrics;
import com.sun.javafx.tk.Toolkit;

import javafx.geometry.Bounds;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import letrain.map.Point;
import letrain.mvp.View;

public class ViewGrid extends BorderPane implements View {
    private static final int DEFAULT_ROWS = 40;
    private static final int DEFAULT_COLS = 100;
    private static final int TEXT_SIZE = 10;
    private final GraphicsContext gc;
    private final Canvas canvas;
    private static Font font = Font.font("Monospace", TEXT_SIZE);
    int cols = DEFAULT_COLS;
    int rows = DEFAULT_ROWS;
    private int zoom = 10;

    private Point gridPositionInMap = new Point(0, 0);

    private float charWidth;
    private float charHeight;

    private double lastMouseX;
    private double lastMouseY;

    private boolean isDragging;

    private double currentTranslateX;
    private double currentTranslateY;

    public ViewGrid() {
        setStyle("-fx-background-color: black;");
        CharSize s = getCharSize("M", font);
        charWidth = s.width;
        charHeight = s.height;
        canvas = new Canvas(cols * charWidth, rows * charHeight);
        setCenter(canvas);
        gc = canvas.getGraphicsContext2D();
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(font);
        addListener();
    }

    void addListener() {
        canvas.setOnMousePressed(event -> {
            lastMouseX = event.getX();
            lastMouseY = event.getY();
            isDragging = true;
        });

        canvas.setOnMouseReleased(event -> {
            isDragging = false;
        });

        canvas.setOnMouseDragged(event -> {
            if (isDragging) {
                double deltaX = event.getX() - lastMouseX;
                double deltaY = event.getY() - lastMouseY;
                currentTranslateX += deltaX;
                currentTranslateY += deltaY;

                lastMouseX = event.getX();
                lastMouseY = event.getY();
            }
        });
        canvas.setOnScroll((ScrollEvent event) -> {
            double zoomFactor = event.getDeltaY();
            this.zoom += zoomFactor > 0 ? 1 : -1;
            event.consume();
            recalcFontSize();
        });
    }

    void recalcFontSize() {
        this.font = Font.font("Monospace", TEXT_SIZE + zoom);
        CharSize s = getCharSize("M", font);
        charWidth = s.width;
        charHeight = s.height;
        double height = getHeight();
        double width = getWidth();
        cols = (int) (width / charWidth);
        rows = (int) (height / charHeight);
        canvas.setWidth(cols * charWidth);
        canvas.setHeight(rows * charHeight);
        gc.setFont(this.font);
    }

    @Override
    public Point getGridPositionInMap() {
        return this.gridPositionInMap;
    }

    @Override
    public void setGridPositionInMap(Point pos) {
        this.gridPositionInMap = pos;
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
        x -= (getGridPositionInMap().getX() * cols) - ((currentTranslateX / charWidth));
        y -= (getGridPositionInMap().getY() * rows) - ((currentTranslateY / charHeight));
        if (x >= 0 && x < cols && y >= 0 && y < rows) {
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
        setGridPositionInMap(new Point(pageX, pageY));
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
    public void setHelpBarText(String info) {
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

    public record CharSize(float width, float height) {
    }

    public CharSize getCharSize(String s, Font font) {
        Text text = new Text(s);
        text.setFont(font);
        Bounds tb = text.getBoundsInLocal();
        Rectangle stencil = new Rectangle(
                tb.getMinX(),
                tb.getMinY(),
                tb.getWidth(),
                tb.getHeight());
        Shape intersection = Shape.intersect(text, stencil);
        Bounds ib = intersection.getBoundsInLocal();
        return new CharSize((float) (ib.getWidth()), (float) (ib.getHeight()));
    }

}