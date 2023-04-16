package letrain.mvp.impl;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import letrain.map.Point;
import letrain.mvp.GameViewListener;

public class View extends BorderPane implements letrain.mvp.View {
    private final GameViewListener gameViewListener;
    private final ViewGrid viewGrid;
    private Text statusBar = new Text();
    private Text infoBar = new Text();
    private Text helpBar = new Text();

    public View(GameViewListener gameViewListener) {
        viewGrid = new ViewGrid();
        this.gameViewListener = gameViewListener;
        setCenter(viewGrid);
        VBox bottomBox = new VBox();
        bottomBox.setStyle("-fx-background-color: #000000;");

        helpBar.setFill(Color.WHITE);
        helpBar.setStroke(Color.WHITE);
        helpBar.setFont(new Font("Lucida Sans Unicode", 12));
        helpBar.setWrappingWidth(1200);
        helpBar.setWrappingWidth(getWidth());

        statusBar.setFill(Color.GREEN);
        statusBar.setStroke(Color.GREEN);
        statusBar.setFont(new Font("Lucida Sans Unicode", 12));
        statusBar.setWrappingWidth(getWidth());

        infoBar.setFill(Color.YELLOW);
        infoBar.setStroke(Color.YELLOW);
        infoBar.setFont(new Font("Lucida Sans Unicode", 12));
        infoBar.setWrappingWidth(getWidth());

        bottomBox.getChildren().add(statusBar);
        bottomBox.getChildren().add(infoBar);
        bottomBox.getChildren().add(helpBar);

        setBottom(bottomBox);
        this.setFocusTraversable(true);
        clear();
        addEventListener();
        this.requestFocus();
    }

    private void addEventListener() {
        addEventHandler(KeyEvent.KEY_PRESSED, keyEvent -> {
            gameViewListener.onChar(keyEvent);
        });
        widthProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observableValue, Number oldSceneWidth,
                    Number newSceneWidth) {
                viewGrid.recalcFontSize();
                viewGrid.paint();
            }
        });
        heightProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observableValue, Number oldSceneHeight,
                    Number newSceneHeight) {
                viewGrid.recalcFontSize();
                viewGrid.paint();
            }
        });
    }

    @Override
    public Point getMapScrollPage() {
        return viewGrid.getMapScrollPage();
    }

    @Override
    public void setMapScrollPage(Point pos) {
        viewGrid.setMapScrollPage(pos);
        setStatusBarText("Página: " + viewGrid.getMapScrollPage().getX() + ", " + viewGrid.getMapScrollPage().getY());
    }

    public void setStatusBarText(String text) {
        statusBar.setText(text);
    }

    public void setInfoBarText(String text) {
        infoBar.setText(text);
    }

    @Override
    public void setHelpBarText(String info) {
        helpBar.setText(info);
    }

    @Override
    public void paint() {
        viewGrid.paint();
    }

    @Override
    public void clear() {
        viewGrid.clear();
    }

    @Override
    public void set(int x, int y, String c) {
        viewGrid.set(x, y, c);
    }

    @Override
    public void setColor(Color color) {
        viewGrid.setColor(color);
    }

    @Override
    public void setPageOfPos(int x, int y) {
        viewGrid.setPageOfPos(x, y);
        setStatusBarText("Página: " + viewGrid.getMapScrollPage().getX() + ", " + viewGrid.getMapScrollPage().getY());
    }

    @Override
    public void clear(int x, int y) {
        viewGrid.clear(x, y);
    }

    @Override
    public void fill(int x, int y, int width, int height, String c) {
        viewGrid.fill(x, y, width, height, c);
    }

    @Override
    public void box(int x, int y, int width, int height) {
        viewGrid.box(x, y, width, height);
    }

    @Override
    public void incZoom() {
        viewGrid.incZoom();
    }

    @Override
    public void decZoom() {
        viewGrid.decZoom();
    }

    @Override
    public void resetZoom() {
        viewGrid.resetZoom();
    }

}
