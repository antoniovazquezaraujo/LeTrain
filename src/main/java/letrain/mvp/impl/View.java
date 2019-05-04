package letrain.mvp.impl;

import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import letrain.map.Point;
import letrain.mvp.Model;
import letrain.mvp.GameViewListener;


public class View extends BorderPane implements letrain.mvp.View {
    private final GameViewListener gameViewListener;
    private final ViewGrid viewGrid;
    private Point position = new Point(0, 0); // scroll position of the viewer
    private Text statusBar = new Text();
    private Text infoBar = new Text();

    public View(GameViewListener gameViewListener) {
        viewGrid = new ViewGrid();
        this.gameViewListener = gameViewListener;
        setTop(createMenuBar());
        setCenter(viewGrid);
        HBox bottomBox = new HBox();
        statusBar.setWrappingWidth(100);
        infoBar.setWrappingWidth(500);
        bottomBox.getChildren().add(statusBar);
        bottomBox.getChildren().add(infoBar);
        setBottom(bottomBox);
        this.setFocusTraversable(true);
        clear();
        addEventListener();
        this.requestFocus();
        statusBar.setFill(Color.GREEN);
        statusBar.setStroke(Color.GREEN);
        statusBar.setFont(new Font("Lucida Sans Unicode", 10));
    }

    public Node createMenuBar() {
        MenuBar menuBar = new MenuBar();
        Menu menu = new Menu("Actions");
        menuBar.getMenus().add(menu);
        for (Model.GameMode option : Model.GameMode.values()) {
            MenuItem item = new MenuItem(option.getName());
            menu.getItems().add(item);
            item.setOnAction(t -> {
                doCommand(option);
            });
        }
        return menuBar;
    }

    public void doCommand(Model.GameMode mode) {
        gameViewListener.onGameModeSelected(mode);
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
        statusBar.setText("Página: " + viewGrid.getMapScrollPage().getX() + ", " + viewGrid.getMapScrollPage().getY());
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
        statusBar.setText("Página: " + viewGrid.getMapScrollPage().getX() + ", " + viewGrid.getMapScrollPage().getY());
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
    public void showInfo(String info) {

    }
}
