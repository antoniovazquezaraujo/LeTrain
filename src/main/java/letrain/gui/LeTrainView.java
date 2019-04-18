package letrain.gui;

import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import letrain.map.Point;
import letrain.sim.GameModel;
import letrain.tui.BasicGraphicConverter;
import letrain.tui.GraphicConverter;
import letrain.tui.SimpleUI;


public class LeTrainView extends GridPane implements SimpleUI {
    GamePresenter presenter;
    LeTrainViewGrid viewGrid;
    private Point position = new Point(0, 0); // scroll position of the viewer

    public LeTrainView(GamePresenter presenter) {
        viewGrid = new LeTrainViewGrid();
        this.presenter = presenter;
        add(viewGrid, 0,0,1,1);
        this.setFocusTraversable(true);
        clear();
        addEventListener();
        this.requestFocus();
    }

    public void addEventListener() {
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
