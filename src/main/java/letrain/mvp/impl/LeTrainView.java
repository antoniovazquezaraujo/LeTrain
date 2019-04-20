package letrain.mvp.impl;

import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import letrain.map.Point;
import letrain.mvp.GameView;
import letrain.mvp.GameViewListener;


public class LeTrainView extends BorderPane implements GameView {
    private final GameViewListener gameViewListener;
    private final LeTrainViewGrid viewGrid;
    private Point position = new Point(0, 0); // scroll position of the viewer
    private Text statusBar = new Text();

    public LeTrainView(GameViewListener gameViewListener) {
        viewGrid = new LeTrainViewGrid();
        this.gameViewListener = gameViewListener;
        setTop(createMenuBar());
        setCenter(viewGrid );
        setBottom(statusBar);
        this.setFocusTraversable(true);
        clear();
        addEventListener();
        this.requestFocus();
        statusBar.setFill(Color.GREEN);
        statusBar.setStroke(Color.GREEN);
        statusBar.setFont(new Font("Lucida Sans Unicode", 10));
    }
    public Node createMenuBar(){
        MenuBar menuBar = new MenuBar();
        Menu menu = new Menu("Actions");
        menuBar.getMenus().add(menu);
        for(GameView.GameMode option: GameView.GameMode.values()){
            MenuItem item = new MenuItem(option.getName());
            menu.getItems().add(item);
            item.setOnAction(t->{doCommand(option);});
        }
        return menuBar;
    }
    public void doCommand(GameView.GameMode mode){
        gameViewListener.onGameModeSelected(mode);
    }

    private void addEventListener() {
        addEventHandler(KeyEvent.KEY_PRESSED, keyEvent -> {
            switch (keyEvent.getCode()) {
                case UP:
                    if (keyEvent.isControlDown()) {
                        clear();
                        Point p = getMapScrollPage();
                        p.setY(p.getY() - 1);
                        setMapScrollPage(p);
                        clear();
                    } else {
                        gameViewListener.onUp();
                    }
                    break;
                case DOWN:
                    if (keyEvent.isControlDown()) {
                        clear();
                        Point p = getMapScrollPage();
                        p.setY(p.getY() + 1);
                        setMapScrollPage(p);
                        clear();
                    } else {
                        gameViewListener.onDown();
                        gameViewListener.onUp();
                        gameViewListener.onDown();
                    }
                    break;
                case LEFT:
                    if (keyEvent.isControlDown()) {
                        clear();
                        Point p = getMapScrollPage();
                        p.setX(p.getX() - 1);
                        setMapScrollPage(p);
                        clear();
                    } else {
                        gameViewListener.onLeft();
                    }
                    break;
                case RIGHT:
                    if (keyEvent.isControlDown()) {
                        clear();
                        Point p = getMapScrollPage();
                        p.setX(p.getX() + 1);
                        setMapScrollPage(p);
                        clear();
                    } else {
                        gameViewListener.onRight();
                    }
                    break;
                case Q:
                    System.exit(0);
                    break;
                default:
                    break;
            }
        });
        addEventHandler(KeyEvent.KEY_TYPED, keyEvent -> {
            gameViewListener.onChar(keyEvent.getCharacter());
        });
    }

    @Override
    public Point getMapScrollPage() {
        return viewGrid.getMapScrollPage();
    }

    @Override
    public void setMapScrollPage(Point pos) {
        viewGrid.setMapScrollPage(pos);
        statusBar.setText("Página: "+ viewGrid.getMapScrollPage().getX()+ ", "+ viewGrid.getMapScrollPage().getY());
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
        viewGrid.setPageOfPos(x,y);
        statusBar.setText("Página: "+ viewGrid.getMapScrollPage().getX()+ ", "+ viewGrid.getMapScrollPage().getY());
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
