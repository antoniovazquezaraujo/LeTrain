package letrain.mvp.impl;

import com.sun.javafx.tk.FontMetrics;
import com.sun.javafx.tk.Toolkit;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import letrain.mvp.GameViewListener;
import letrain.mvp.View;
import letrain.physics.Vector2D;


public class ViewGrid extends Pane implements View {
    private static final int ROWS = 40;
    private static final int COLS = 100;
    private final GameViewListener gameViewListener;
    private Vector2D mapScrollPage = new Vector2D(0, 0);
    private final Canvas canvas;
    private final GraphicsContext gc;
    private static final int TEXT_SIZE = 20;
    private static final Font font = Font.font("Monospace", TEXT_SIZE);

    private final float charWidth;
    private final float charHeight;

    public ViewGrid(GameViewListener gameViewListener) {
        this.gameViewListener = gameViewListener;
        setStyle("-fx-background-color: black;");
        FontMetrics metrics = Toolkit.getToolkit().getFontLoader().getFontMetrics(font);
        charWidth = metrics.computeStringWidth("X");
        charHeight = metrics.getLineHeight();
        canvas = new Canvas(COLS * charWidth, ROWS * charHeight);
        getChildren().add(canvas);
        gc = canvas.getGraphicsContext2D();
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(font);
    }
    private void addEventListener() {
        addEventHandler(KeyEvent.KEY_PRESSED, keyEvent -> {
            gameViewListener.onChar(keyEvent);
        });
    }
    @Override
    public Vector2D getMapScrollPage() {
        return this.mapScrollPage;
    }

    @Override
    public void setMapScrollPage(Vector2D pos) {
        this.mapScrollPage = pos;
    }


    @Override
    public void paint() {

    }

    @Override
    public void clear() {
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    @Override
    public void set(double x, double y, String c) {
        x -= mapScrollPage.getX() * COLS;
        y -= mapScrollPage.getY() * ROWS;
        if (x >= 0 && x < COLS && y >= 0 && y < ROWS) {

            //TODO probar diferentes grosores para diferentes elementos???
            gc.setLineWidth(1);


            // Aquí usamos (ROWS-y) en lugar de y porque en pantalla, las coordenadas verticales
            // comienzan desde arriba, pero internamente, se calcula siempre desde abajo.
            gc.strokeText(c, x * charWidth,  (ROWS-y) * charHeight );
        }
    }

    @Override
    public void setColor(Color color) {
        gc.setStroke(color);
    }

    @Override
    public void setPageOfPos(double x, double y) {
        if (x < 0) x -= COLS;
        if (y < 0) y -= ROWS;
        int pageX = (int)x / COLS;
        int pageY = (int)y / ROWS;
        setMapScrollPage(new Vector2D(pageX, pageY));
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
}