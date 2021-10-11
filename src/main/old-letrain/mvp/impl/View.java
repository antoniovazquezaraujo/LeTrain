package letrain.mvp.impl;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import letrain.map.Point;
import letrain.mvp.Model;
import letrain.mvp.GameViewListener;


public class View extends StackPane implements letrain.mvp.View {
    private final GameViewListener gameViewListener;
    private final ViewGrid viewGrid;
    private Point position = new Point(0, 0); // scroll position of the viewer
    private Text statusBar;
    private Text infoBar;
    private Text helpBar;

    public View(GameViewListener gameViewListener) {
        viewGrid = new ViewGrid(gameViewListener);
        this.gameViewListener = gameViewListener;
//        setCenter(viewGrid);

        getChildren().add(viewGrid);
        StackPane.setAlignment(viewGrid, Pos.CENTER);

        statusBar = new Text();
        statusBar.setFill(Color.WHITE);
        statusBar.setFont(new Font("Lucida Sans Unicode", 12));
        statusBar.setWrappingWidth(200);

        infoBar = new Text();
        infoBar.setFill(Color.WHITE);
        infoBar.setFont(new Font("Lucida Sans Unicode", 12));
        infoBar.setWrappingWidth(1000);

        helpBar = new Text();
        helpBar.setFill(Color.WHITE);
        helpBar.setFont(new Font("Lucida Sans Unicode", 12));
        helpBar.setWrappingWidth(1200);

        HBox infoAndStatusBox = new HBox();
        infoAndStatusBox.setPrefHeight(50);
        infoAndStatusBox.getChildren().add(statusBar);
        infoAndStatusBox.getChildren().add(infoBar);
        HBox helpBox = new HBox();
        helpBox.setPrefHeight(50);
        helpBox.getChildren().add(helpBar);

        VBox bottomBox = new VBox();
        bottomBox.setPrefHeight(100);
        bottomBox.getChildren().add(infoAndStatusBox);
        bottomBox.getChildren().add(helpBox);
        getChildren().add(bottomBox);
        bottomBox.setMaxHeight(100);
        StackPane.setAlignment(bottomBox, Pos.BOTTOM_CENTER);



//        setBottom(bottomBox);
        this.setFocusTraversable(true);
        clear();
        addEventListener();
        this.requestFocus();
    }

    private void addEventListener() {
        addEventHandler(KeyEvent.KEY_PRESSED, keyEvent -> {
            gameViewListener.onChar(keyEvent);
        });
    }

    @Override
    public Point getMapScrollPage() {
        return viewGrid.getMapScrollPage();
    }

    @Override
    public void setMapScrollPage(Point pos) {
        viewGrid.setMapScrollPage(pos);
        setStatusBarText("Page: " + viewGrid.getMapScrollPage().getX() + ", " + viewGrid.getMapScrollPage().getY());
    }

    public void setStatusBarText(String text){
        statusBar.setText(text);
    }
    public void setInfoBarText(String text){
        infoBar.setText(text);
    }
    public void setHelpBarText(String text){helpBar.setText(text);}

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
        setStatusBarText("Page: " + viewGrid.getMapScrollPage().getX() + ", " + viewGrid.getMapScrollPage().getY());
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

}
