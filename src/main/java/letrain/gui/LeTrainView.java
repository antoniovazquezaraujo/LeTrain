package letrain.gui;

import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import letrain.map.Point;
import letrain.sim.GameModel;
import letrain.tui.BasicGraphicConverter;
import letrain.tui.GraphicConverter;
import letrain.tui.SimpleUI;


public class LeTrainView extends BorderPane implements SimpleUI {
    private final GamePresenter presenter;
    private final LeTrainViewGrid viewGrid;
    private Point position = new Point(0, 0); // scroll position of the viewer
    private Text statusBar = new Text();
    public LeTrainView(GamePresenter presenter) {
        viewGrid = new LeTrainViewGrid();
        this.presenter = presenter;
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

    private void addEventListener() {
        GraphicConverter converter = new BasicGraphicConverter();

        addEventHandler(KeyEvent.KEY_PRESSED, keyEvent -> {
            switch (keyEvent.getCode()) {
                case Y:
                    presenter.paintLoop();
                case ENTER:
                    break;
                case UP:
                    if (keyEvent.isControlDown()) {
                        clear();
                        Point p = getMapScrollPage();
                        p.setY(p.getY() - 1);
                        setMapScrollPage(p);
                        clear();
                    } else {
                        presenter.onMakerAdvance();
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
                        presenter.onMakerInverse();
                        presenter.onMakerAdvance();
                        presenter.onMakerInverse();
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
                        presenter.onMakerTurnLeft();
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
                        presenter.onMakerTurnRight();
                    }
                    break;
                case M:
                    presenter.onGameModeSelected(GameModel.Mode.MAP_WALK);
                    break;
                case T:
                    presenter.onGameModeSelected(GameModel.Mode.TRACK_WALK);
                    break;
                case P:
                    presenter.onGameModeSelected(GameModel.Mode.MAKE_TRACK);
                    break;
                case R:
                    presenter.onGameModeSelected(GameModel.Mode.REMOVE_TRACK);
                    break;
                case W:
                    presenter.onFactoryCreateTrain("A new train");
                    break;
                case S:
                    presenter.onIncTrainAcceleration();
                    break;
                case A:
                    presenter.onDecTrainAcceleration();
                    break;
//                case F:
//                    this.model.moveTrains();
//                    break;
                case Q:
                    System.exit(0);
                    break;
                default:
                    break;
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
